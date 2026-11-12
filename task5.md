# Task 5 — N+1, performance tuning and a second migration

A concept note with **run diagrams**: what happens in Java, in Hibernate and in the database at each
step of Task 5. It continues [task1.md](task1.md), [task2.md](task2.md), [README-task3.md](README-task3.md)
and Task 4, and it is written against the code that is on `Lab03` now.

```text
 ┌──────────────┐    ┌──────────────┐    ┌──────────────────┐    ┌──────────────┐    ┌──────────────┐
 │ 1. REPRODUCE │───►│ 2. MEASURE   │───►│ 3. FIX 3 WAYS    │───►│ 4. TUNE      │───►│ 5. EVOLVE    │
 │    N+1       │    │ QueryCounter │    │ join fetch       │    │ JDBC batch   │    │ SCHEMA       │
 │ (51 queries) │    │ + timings    │    │ @EntityGraph     │    │ open-in-view │    │ V2__indexes  │
 │              │    │              │    │ @BatchSize       │    │ = false      │    │ + EXPLAIN    │
 └──────────────┘    └──────────────┘    └──────────────────┘    └──────────────┘    └──────────────┘
```

---

## 0. Before you start: the `Lab03` code must build

Task 5 runs `@SpringBootTest`s, so the whole application has to compile and start. On the current `Lab03`
(commit `a6f233d`) it does not yet. This was found by running `./mvnw test`:

| Problem | Where | Why | Fix |
| --- | --- | --- | --- |
| compile error: `cannot find symbol list(...)` / `ordersOf(...)` | `OrderController`, `CustomerController` | the Task 4 commit replaced `OrderService` and dropped the Task 3 methods | put `list(status, pageable)` and `ordersOf(email)` back into `OrderService` (they are in commit `4febb9c`), next to the Task 4 methods |
| `NULL not allowed for column "ID"` on `insert into tag` | `FlushOrderTest`, `DataSeeder` | `Tag` uses `GenerationType.IDENTITY`, but `V1__init.sql` creates `tag.id BIGINT PRIMARY KEY` + `tag_seq` (no identity column) | use `SEQUENCE` with `@SequenceGenerator(name = "tag_seq", sequenceName = "tag_seq", allocationSize = 50)` |
| `expected: 2L but was: 3L` | `OrderServiceTest.detachedChangeIsLostUntilSaveMerges` | the counter is read **after** one more `findById` (merge SELECT + UPDATE + reload SELECT = 3) | read the statistics right after `products.save(p)` |

```text
 after the 3 fixes:   ./mvnw test   →   Tests run: 15, Failures: 0, Errors: 0   BUILD SUCCESS
```

The `Tag` bug is also a live preview of section 8: **`IDENTITY` needs an identity column in the database.**

---

## 1. What already exists and what Task 5 adds

```text
 EXISTS on Lab03                                   TASK 5 ADDS
 ───────────────                                   ───────────
 Order.lines  @OneToMany(LAZY) List<OrderLine>     + @BatchSize(size = 16)              (Fix C, see 6)
 OrderLine.product  @ManyToOne(LAZY)               (the "product name" regression, see 8)
 OrderRepository                                   + List<Order> findByStatus(OrderStatus)       (naive)
   Page<OrderSummary> findByStatus(status, page)   + findByStatusWithLines(s)                    (Fix A)
   findWithLinesByCustomerEmail (join fetch)       + findWithGraphByStatus(status)               (Fix B)
 OrderService.view(id) → OrderDto (inside tx)      (already the open-in-view fix, see 9)
 test/support/TestData  newOrder(), newProductId() + paidOrders(50, 3)
 application.yml  generate_statistics: true        + jdbc.batch_size, order_inserts, order_updates, batch TRACE
 db/migration/V1__init.sql                         + V2__indexes.sql   (V1 stays untouched)

 NEW FILES
 service/ReportService.java            4 report variants (+ product-name variants)
 test/.../support/QueryCounter.java    statement counter
 test/.../NPlusOneTest.java            51 / 1 / 1 / 5
 test/.../BatchInsertTest.java         1 000 inserts in a few batches
 test/.../OpenInViewTest.java          LazyInitializationException outside the transaction
```

`OrderRepository` may have both `findByStatus` methods: Java allows the overload (different parameters),
and Spring Data builds a separate query for each.

---

## 2. Concept: the N+1 problem

`Order.lines` is **LAZY**: loading an order does **not** load its lines. The first call to
`getLines().size()` sends one extra query **for that order only**.

```java
@Service
@Transactional(readOnly = true)
public class ReportService {
    public Map<Long, Integer> linesPerPaidOrder() {
        Map<Long, Integer> result = new LinkedHashMap<>();
        for (Order o : orders.findByStatus(OrderStatus.PAID)) {    // 1 query
            result.put(o.getId(), o.getLines().size());             // +1 query PER order
        }
        return result;
    }
}
```

### Run diagram — naive loop

```text
 SERVICE CODE                         HIBERNATE                         DATABASE
 ────────────                         ─────────                         ────────
 findByStatus(PAID) ────────────────► 1 query ────────────────────────► select ... from orders where status=?
                                      50 Order objects,                   ◄── 50 rows
                                      each lines = LAZY (not loaded)

 loop order #1: getLines().size() ──► collection not loaded ──────────► select ... from order_line where order_id=?   (1)
 loop order #2: getLines().size() ──► collection not loaded ──────────► select ... from order_line where order_id=?   (2)
 ...                                                                     ...
 loop order #50: getLines().size() ─► collection not loaded ──────────► select ... from order_line where order_id=?   (50)

                                      TOTAL = 1 + N = 1 + 50 = 51 statements
```

Why it is dangerous: the code looks innocent, and with 5 orders in development nobody notices.
In production with 10 000 orders it becomes 10 001 round trips.

---

## 3. Concept: counting statements (`QueryCounter`)

```text
            ┌─────────────────────── one SessionFactory (shared by the whole app) ───────────────────────┐
            │   Statistics  (generate_statistics: true, already in application.yml)                      │
 every SQL  │     prepareStatementCount  ++  each time Hibernate prepares a JDBC statement               │
 ─────────► │     entityInsertCount      ++  each entity inserted                                        │
            └────────────────────────────────────────────────────────────────────────────────────────────┘
                     ▲                            ▲
        counter.reset()  → stats.clear()     counter.count() → getPrepareStatementCount()
```

```text
 seed data (many SQL)   reset()        call ONE service method      count()
 ──────────────────────│──────────────────────────────────────────│────────►
                       0             only these statements          51
```

| Detail | Why |
| --- | --- |
| `reset()` right **before** the call | the seeding SQL must not be counted |
| read `count()` right **after** the call | any later `findById` adds statements (the `OrderServiceTest` bug in section 0) |
| `@Execution(ExecutionMode.SAME_THREAD)` | the statistics are global; tests running in parallel would mix their counts |
| assert an **exact** number (51, 1, 5) | the build **fails** as soon as somebody reintroduces N+1 |
| `@Tag("demo")` on the naive test | it documents the problem; it can be selected or excluded by tag |

`OrderServiceTest` already does the same thing by hand (`emf.unwrap(SessionFactory.class).getStatistics()`);
`QueryCounter` just wraps it as a reusable `@Component` in `src/test/.../support`, next to `TestData`.

### `TestData.paidOrders(50, 3)`

```text
 paidOrders(50, 3)            (@Transactional, like newOrder())
   │
   ├─ already 50 PAID orders from this helper?  → return   (idempotent: @SpringBootTest does not roll back)
   ├─ 1 customer + 3 products   (UUID in sku/email, like newOrder(), so unique columns never clash)
   └─ 50 × new Order(c); setStatus(PAID); addLine(p1..p3, 1)  → orders.save(o)   (cascade saves lines)
```

> Other tests (`OrderServiceTest`, `OptimisticLockingTest`) create orders too and do not roll back.
> The report reads **all** PAID orders, so "exactly 50" only holds if nothing else creates PAID orders in the
> same database. `OrderServiceTest.markPaid...` does create one. Count the PAID orders before the call (or seed
> into a known state) instead of hard-coding 50 if the numbers drift.

---

## 4. Fix A — `join fetch`

```java
@Query("select distinct o from Order o join fetch o.lines where o.status = :s")
List<Order> findByStatusWithLines(@Param("s") OrderStatus s);
```

```text
 findByStatusWithLines(PAID) ──► ONE query with a JOIN ──────────────► select o1_0.*, l1_0.*
                                                                         from orders o1_0
                                                                         join order_line l1_0 on o1_0.id=l1_0.order_id
                                                                         where o1_0.status=?
                                                                        ◄── 150 rows (50 orders × 3 lines)
                                 Hibernate groups rows by order id
                                 → 50 Orders, each lines ALREADY loaded

 loop: getLines().size()  ─────► no SQL (collection initialised)

                                 TOTAL = 1 statement
```

* `join` is an **inner join**: an order with **zero** lines is not returned. Use `left join fetch` if that matters.
* `distinct`: 150 rows contain each order 3 times. Hibernate 6+ removes the duplicates by itself; `distinct` is harmless.
* The existing `findWithLinesByCustomerEmail` (Task 3) is the same technique, with `join fetch l.product` as well.
* Do **not** combine `join fetch` of a collection with paging (`Pageable`): Hibernate then pages **in memory**
  (warning `HHH90003004`). That is why `/api/orders` uses the `OrderSummary` projection instead.

---

## 5. Fix B — `@EntityGraph`

```java
@EntityGraph(attributePaths = {"lines", "lines.product"})
List<Order> findWithGraphByStatus(OrderStatus status);
```

```text
 findWithGraphByStatus(PAID)
     │  Spring Data builds the query from the method name (text between "find" and "By" is ignored)
     │  and adds a "fetch graph": load lines AND each line's product too
     ▼
 ONE query ──────────────────────────────────────────────────────────► select ...
                                                                         from orders o1_0
                                                                         left join order_line l1_0 on ...
                                                                         left join product p1_0 on p1_0.id=l1_0.product_id
                                                                         where o1_0.status=?
                                 TOTAL = 1 statement
```

| | join fetch (A) | @EntityGraph (B) |
| --- | --- | --- |
| where it lives | in the JPQL string | annotation on a derived repository method |
| join type | what you write (`join` = inner) | `left join` (orders without lines are kept) |
| also loads products | only if you add `join fetch l.product` | yes, because of `"lines.product"` |

---

## 6. Fix C — `@BatchSize(size = 16)`

Keep the naive loop, change the **mapping** in `Order`:

```java
@OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
@BatchSize(size = 16)                       // org.hibernate.annotations.BatchSize
private List<OrderLine> lines = new ArrayList<>();
```

When one lazy `lines` collection is touched, Hibernate also loads up to 15 **other** uninitialised
`lines` collections from the same persistence context in the **same** query.

```text
 findByStatus(PAID) ───────────────────────────────────────────────► select ... from orders where status=?      (1)

 order #1.getLines()  ──► load lines of orders  1..16  ─────────────► ... where l1_0.order_id in (?,?,...16 ids) (2)
 order #2..#16        ──► already loaded, no SQL
 order #17.getLines() ──► load lines of orders 17..32  ─────────────► ... where l1_0.order_id in (16 ids)        (3)
 order #33.getLines() ──► load lines of orders 33..48  ─────────────► ... where l1_0.order_id in (16 ids)        (4)
 order #49.getLines() ──► load lines of orders 49..50  ─────────────► ... where l1_0.order_id in (2 ids)         (5)

        TOTAL = 1 + ceil(50 / 16) = 1 + 4 = 5 statements
```

> On some Hibernate/dialect combinations the batch appears as `where l1_0.order_id = any (?)` with one
> array parameter instead of `in (?,?,...)`. It is the same batch, and the count is the same.

### ⚠ The trap: `@BatchSize` also changes the naive test

`@BatchSize` is on the **mapping**, so it applies to **every** code path that touches `Order.lines`,
including `linesPerPaidOrder()`. In Hibernate 7.4.5 (this project) the rule is in
`LoadQueryInfluencers.effectiveBatchSize`: **the mapping's batch size wins** over anything set on the session.

```text
 without @BatchSize:   naive = 51     batched = 51     ✗ Fix C shows nothing
 with    @BatchSize:   naive =  5     batched =  5     ✗ the demo test asserting 51 fails
```

So the handout's "51 / 1 / 1 / 5 in one green build" cannot all hold with the annotation on `Order.lines`.
Choose one way and **write it in the README**:

| Option | Naive test | Batched test | Note |
| --- | ---: | ---: | --- |
| a) record 51 **before** adding `@BatchSize`, then keep the demo test as `@Tag("demo")` and run the normal build with `-DexcludedGroups=demo` | 51 (before) | 5 | follows the handout's annotation |
| b) no annotation; `linesPerPaidOrderBatched()` calls `em.unwrap(Session.class).setFetchBatchSize(16)` and then runs the same loop | 51 | 5 | same SQL, both tests always green; the setting only lives in that one session |

For the same reason, `default_batch_fetch_size` must **not** be active for the 51 test (section 8).

---

## 7. Comparing the variants and measuring time

| Variant | Statements | How the lines arrive |
| --- | ---: | --- |
| naive (lazy loop) | 51 | 1 query per order |
| join fetch | 1 | one join |
| @EntityGraph | 1 | one left join (plus products) |
| @BatchSize(16) | 5 | 1 + 4 `IN` batches |

### How to measure the median

```text
 warm-up (e.g. 5 runs, ignored) ──► JIT compiles, caches fill, connections open
        │
        ▼
 20 timed runs:  t = System.nanoTime(); report.variant(); t = System.nanoTime() - t
        │
        ▼
 sort the 20 times ──► median = average of the 10th and 11th value  (in ms)
```

* **Median, not average**: one slow run (GC pause) does not move the median.
* Each call is a new service call = a **new** transaction and persistence context, so the first-level cache
  cannot hide queries between runs.
* `org.hibernate.SQL: DEBUG` and `org.hibernate.orm.jdbc.bind: TRACE` are on in `application.yml`; printing
  hundreds of log lines costs time. Measure with that logging turned down, or all variants pay for logging.
* The numbers depend on your machine. The handout's example values (38.2 / 4.1 / 4.4 / 7.9 ms) show the
  **shape** (naive ≫ batch > join), not values you must match.

README table to fill in:

```text
| variant                 | statements | median ms |
|-------------------------|-----------:|----------:|
| naive (lazy loop)       |         51 |    (yours)|
| join fetch              |          1 |    (yours)|
| @EntityGraph            |          1 |    (yours)|
| @BatchSize(16)          |          5 |    (yours)|
```

---

## 8. The "product name" regression and `default_batch_fetch_size`

Now the report also reads `line.getProduct().getName()`. `OrderLine.product` is `@ManyToOne(LAZY)`, so each
**distinct** product not loaded yet costs one more query. A product loaded once stays in the persistence
context, so it is "one per distinct product", not "one per line".

Let **P** = number of distinct products used by the 150 lines.

| Variant | Lines | + products | Total | Regresses? |
| --- | --- | --- | --- | --- |
| naive | 1 + 50 | + P | 51 + P | yes (already bad) |
| join fetch (`join fetch o.lines` only) | 1 | + P | 1 + P | **yes**, N+1 on products |
| @EntityGraph (`lines`, `lines.product`) | 1 | + 0 | 1 | **no**, products are in the same join |
| @BatchSize(16) on lines only | 1 + 4 | + P | 5 + P | **yes**, the batch size is only on `lines` |

How big P is depends on `paidOrders`:

```text
 3 shared products for all orders  → P = 3     regression is small (but still N+1 in principle)
 3 new products per order          → P = 150   regression is obvious (join fetch: 151 statements)
```

Seed with **different** products per order if you want the regression to be visible in the numbers.

### The cap: `hibernate.default_batch_fetch_size=32`

A **global** batch size for every lazy association and collection that has no `@BatchSize` of its own:

```text
 first product proxy touched ──► load up to 32 product proxies at once ──► select ... from product where id in (32 ids)

 products: ceil(P / 32)      P = 150 → 5 queries instead of 150
```

| Variant (P = 150) | Without the cap | With `default_batch_fetch_size=32` |
| --- | ---: | ---: |
| join fetch | 1 + 150 = 151 | 1 + 5 = 6 |
| @BatchSize(16) | 1 + 4 + 150 = 155 | 1 + 4 + 5 = 10 |
| @EntityGraph | 1 | 1 |

It does not remove the extra queries, it **caps** them: from "one per row" to "one per 32 rows".

> Turn it on **only** for this experiment, for example on that test class:
> `@SpringBootTest(properties = "spring.jpa.properties.hibernate.default_batch_fetch_size=32")`.
> If it is in `application.yml`, the naive test no longer shows 51 (it becomes 1 + ceil(50/32) = 3).
> A different property set makes Spring start a separate test context, which is fine.

---

## 9. Concept: JDBC batching

```yaml
spring:
  jpa:
    properties:
      hibernate:
        jdbc.batch_size: 50     # send up to 50 statements in one round trip
        order_inserts: true     # group inserts by table, so batches are not broken
        order_updates: true     # same for updates
logging:
  level:
    org.hibernate.engine.jdbc.batch: TRACE   # prints "Executing JDBC batch"
```

### Without vs with batching

```text
 WITHOUT batching (1 000 round trips)            WITH batch_size = 50 (20 round trips for inserts)

 app ── insert p1 ──► DB                         app ── [insert p1 … p50]   ──► DB   Executing JDBC batch (50 / 50)
 app ── insert p2 ──► DB                         app ── [insert p51 … p100] ──► DB   Executing JDBC batch (50 / 50)
 app ── insert p3 ──► DB                         ...
 ... 1 000 times                                 app ── [insert p951 … p1000] ─► DB  (20 batches)
```

### Why `order_inserts` matters

```text
 save order1 (+ its line), order2 (+ its line) ...

 order_inserts = false:  [orders][order_line][orders][order_line]...   table changes → batch flushed every time
 order_inserts = true:   [orders, orders, ...][order_line, order_line, ...]   → long batches per table
```

### Run diagram — `BatchInsertTest`

```text
 @Transactional test (one transaction)
 products.saveAll(1 000 new Products)   (unique sku ≤ 20 chars, e.g. "B-" + 8-char UUID + "-" + i)
   │   id == null → isNew → em.persist(p)  (no INSERT yet)
   │   id from product_seq: allocationSize = 50 → 1 "next value" call gives 50 ids
   ▼
 flush ─► about 20 × "select next value for product_seq"
          about 20 × "Executing JDBC batch (50 / 50) - insert into product ..."

 assert stats.getEntityInsertCount() == 1000          ✓ 1 000 entities inserted
 assert stats.getPrepareStatementCount() < 1000       ✓ about 40, far below 1 000
```

Call `flush()` (or `saveAllAndFlush`) **before** the asserts: in a `@Transactional` test the inserts are
otherwise still waiting, and the rollback at the end means they would never be sent.

### SEQUENCE vs IDENTITY

```text
 SEQUENCE (this project)                         IDENTITY (scratch branch only)
 ───────────────────────                         ───────────────────────────────
 Hibernate asks the sequence for ids FIRST       the DB creates the id DURING the insert
 → it knows every id before inserting            → Hibernate must run each insert IMMEDIATELY
 → can queue inserts and send them in batches       to read the id back
                                                 → batching is disabled for inserts
 prepareStatementCount ≈ 40                      prepareStatementCount ≈ 1 000
```

> In the scratch branch, `GenerationType.IDENTITY` on `Product` also needs an identity column
> (`id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY`). With `id BIGINT PRIMARY KEY` the insert fails
> with `NULL not allowed for column "ID"`, which is exactly the `Tag` bug in section 0. Change it in the scratch
> branch only, write the result in the README, and do not merge the branch.

---

## 10. Concept: `open-in-view = false`

`spring.jpa.open-in-view: false` is already in `application.yml`. The persistence context (session) closes when
the `@Transactional` **service or repository** method returns, not at the end of the HTTP request.

```text
 GET /api/orders/{id}/...                                          session
 ──────────────────────────────────────────────────────────────────────────
 Controller ──► orders.findById(id)     (repository's own tx)       OPEN
                returns Order entity                                CLOSED  ◄── transaction ends here
 Controller ──► order.getLines().size()
                  └─► lines is LAZY, needs SQL, no session
                      LazyInitializationException ──► HTTP 500
```

### The evidence test

```text
 @SpringBootTest   (whole app, NOT @Transactional, so no test transaction keeps the session open)

   controller.method(id)  ─► assertThatThrownBy(...).isInstanceOf(LazyInitializationException.class)
   or over HTTP           ─► response status 500
```

> If the test class is `@Transactional` (like `FlushOrderTest`), the test transaction keeps the session open,
> the lazy load works, and the test proves nothing. `OrderServiceTest` has a comment showing the same idea:
> "No transaction here and open-in-view is off, so p is detached right away."
> Calling the controller bean method directly is the simplest version. For the HTTP version, Spring Boot 4 moved
> `TestRestTemplate` into its own test module (`spring-boot-resttestclient`), so check the dependency first.

### The fix already exists: map inside the service

```text
 Controller ──► OrderService.view(id)   @Transactional(readOnly = true)
                  load Order
                  OrderDto.from(o)  → reads lines, products, customer   ◄── still inside the session
                return OrderDto (plain record, nothing lazy)
 Controller ──► return DTO as JSON   ✓ no lazy loading outside the transaction
```

`OrderService.view` + `OrderDto.from` (Task 4) is exactly this pattern. `ordersOf(email)` does the same with a
join fetch, so it is also free of N+1.

`open-in-view = true` (the Spring default) would hide the bug: the session stays open during JSON rendering,
and every lazy access silently becomes another query, often an N+1 in the web layer.

---

## 11. Concept: the second migration `V2__indexes.sql`

```sql
CREATE INDEX idx_orders_customer_placed  ON orders (customer_id, placed_at DESC);
CREATE INDEX idx_order_line_order        ON order_line (order_id);
CREATE INDEX idx_product_category_amount ON product (category, amount);
```

### Which query each index serves (in this code)

| Index | Query it helps |
| --- | --- |
| `orders (customer_id, placed_at DESC)` | customer order lists: `findWithLinesByCustomerEmail`, `findByCustomerEmailAndStatusOrderByPlacedAtDesc`, `findFirstByCustomerIdOrderByPlacedAtDesc` (filter **and** sort from the index) |
| `order_line (order_id)` | loading lines: `where order_id = ?` / `in (...)` (every fix in this task) |
| `product (category, amount)` | `findByCategory` (ProductView page), `findTop3ByCategoryOrderByPriceAmountDesc` |

Column order matters: `(customer_id, placed_at)` helps `where customer_id = ?`, but **not** a query that only
filters on `placed_at` or on `status`.

> **Look closely at `/api/orders`:** on `Lab03` it is `GET /api/orders?status=PAID` →
> `findByStatus(status, pageable)` → `where status = ? order by placed_at desc`. None of the three indexes starts
> with `status`, so its EXPLAIN may show **no change** (a scan of `orders`). That is a valid result to paste: it
> shows the index must match the query. The customer endpoint `/api/customers/{email}/orders` is the one that
> uses `idx_orders_customer_placed`. Run EXPLAIN on both and explain the difference in the README.

### How Flyway applies it

```text
 app start
    │
    ▼
 Flyway reads flyway_schema_history ──► what has been applied?
    │
    ├── V1__init.sql     already applied?  yes → skip  (checksum must still match!)
    └── V2__indexes.sql  new               → run it, add a row to the history
    │
    ▼
 Hibernate ddl-auto: validate ──► app starts

 flyway_schema_history
 ┌───────────┬─────────┬─────────────┬─────────┐
 │ installed │ version │ description │ success │
 ├───────────┼─────────┼─────────────┼─────────┤
 │ 1         │ 1       │ init        │ TRUE    │
 │ 2         │ 2       │ indexes     │ TRUE    │
 └───────────┴─────────┴─────────────┴─────────┘
```

* **Never edit V1.** Flyway stores a checksum per migration; on a persistent database a changed V1 makes start-up
  fail with a checksum mismatch. A schema change is always a **new** file (`V2`, `V3`, ...).
* The database is in memory (`jdbc:h2:mem:shop`), so every start creates it again and runs V1 **and** V2. The log
  then says "Successfully applied **2** migrations ... now at version v2". The handout's "applied 1 migration" is
  what a persistent database shows (only V2 is new there).

### `EXPLAIN` before and after (H2 console)

Start the app with data (the `dev` profile runs `DataSeeder`: 3 customers, 12 products, 30 orders):

```text
 ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
 browser → http://localhost:8080/h2-console   JDBC URL jdbc:h2:mem:shop   user sa   password (empty)
```

```sql
SELECT * FROM flyway_schema_history;

-- /api/orders?status=PAID  (what Hibernate sends for the OrderSummary page, simplified)
EXPLAIN SELECT o.id, o.status, o.placed_at, c.name
          FROM orders o JOIN customer c ON c.id = o.customer_id
         WHERE o.status = 'PAID' ORDER BY o.placed_at DESC;

-- /api/customers/ada@example.com/orders
EXPLAIN SELECT o.* FROM orders o JOIN customer c ON c.id = o.customer_id
         WHERE c.email = 'ada@example.com' ORDER BY o.placed_at DESC;
```

Copy the exact SQL from the `org.hibernate.SQL` log line of the real request if you want the plan of the real
query. What to look for in the plan comments (`/* ... */`):

```text
 tableScan                           → reads the whole table
 CONSTRAINT_INDEX_xx: CUSTOMER_ID=   → H2's automatic foreign-key index (exists already BEFORE V2)
 IDX_ORDERS_CUSTOMER_PLACED: ...     → the V2 index is used (AFTER V2)
 index sorted                        → rows already come out in ORDER BY order, no separate sort
```

> H2 creates an index automatically for each foreign key (`CONSTRAINT_INDEX_...`), so the "before" plan may
> already use an index on `customer_id` / `order_id`. The gain from V2 is mainly the composite index that also
> gives the **sort order**. To get the "before" plan: run EXPLAIN before adding V2, or temporarily
> `DROP INDEX idx_orders_customer_placed` in the console. Paste both real plans into the README.

---

## 12. How a `@SpringBootTest` run works (vs `@DataJpaTest`)

```text
 ./mvnw test -Dtest=NPlusOneTest,BatchInsertTest
        │
        ▼
 @SpringBootTest → starts the WHOLE app (web, services, repositories, JPA)
        │           QueryCounter and TestData are @Components in src/test/.../support → found by the component scan
        ▼
 Flyway V1 + V2 → H2 in memory → Hibernate validate
        │
        ▼
 for each @Test:
   @BeforeEach  data.paidOrders(50, 3)  ← idempotent: seeds only if not there yet
   counter.reset()
   report.variant()                     ← the service opens and closes its own transaction
   assert counter.count() == expected
        │
        ▼
 NO automatic rollback (the test is not @Transactional) → data stays for the next test
 → that is why TestData must be idempotent and use unique values (UUID)
```

| | `@DataJpaTest` (Tasks 1–2) | `@SpringBootTest` (Tasks 4–5) |
| --- | --- | --- |
| what starts | JPA only | the whole application |
| transaction per test | yes, rolled back | no (unless the class is `@Transactional`, like `FlushOrderTest`) |
| lazy loading in the test | works (session open) | fails outside the service, which section 10 needs |
| good for | mapping tests | counting real service calls, web behaviour |

Spring caches the application context: every test class with the same configuration shares **one** app and **one**
in-memory database. A class with different `properties` (section 8) gets its own context and its own database.

---

## 13. How to run

You need **JDK 25** (see [task2.md](task2.md) for installing it). The commands are the same on both systems;
only the wrapper name, the quoting and the log filter differ.

### 13.1 Arch Linux (bash)

```bash
cd SE-LAB-Group3

# whole build first (section 0)
./mvnw test

# N+1 and batching tests (the handout's command)
./mvnw -q test -Dtest=NPlusOneTest,BatchInsertTest

# open-in-view evidence
./mvnw -q test -Dtest=OpenInViewTest

# only the "demo" tests / everything except them
./mvnw test -Dgroups=demo
./mvnw test -DexcludedGroups=demo

# show the SQL of one test, the batches, and count the N+1 queries yourself
./mvnw test -Dtest='NPlusOneTest#batchSizeRunsOnePlusCeilNOverBatch' | grep "org.hibernate.SQL"
./mvnw test -Dtest=BatchInsertTest | grep "Executing JDBC batch"
./mvnw test -Dtest='NPlusOneTest#naiveImplementationRunsNPlusOneQueries' | grep -c "from order_line"

# start the app with demo data → Flyway log + H2 console
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
./mvnw spring-boot:run | grep -i "migrat"
```

### 13.2 Windows (PowerShell)

Put every `-D...` argument in **double quotes**: PowerShell treats `,` and `.` specially.

```powershell
cd SE-LAB-Group3

.\mvnw.cmd test
.\mvnw.cmd -q test "-Dtest=NPlusOneTest,BatchInsertTest"
.\mvnw.cmd -q test "-Dtest=OpenInViewTest"

.\mvnw.cmd test "-Dgroups=demo"
.\mvnw.cmd test "-DexcludedGroups=demo"

.\mvnw.cmd test "-Dtest=NPlusOneTest#batchSizeRunsOnePlusCeilNOverBatch" | Select-String "org.hibernate.SQL"
.\mvnw.cmd test "-Dtest=BatchInsertTest" | Select-String "Executing JDBC batch"
(.\mvnw.cmd test "-Dtest=NPlusOneTest#naiveImplementationRunsNPlusOneQueries" | Select-String "from order_line").Count

.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
.\mvnw.cmd spring-boot:run | Select-String "migrat"
```

### 13.3 Windows (cmd.exe)

```bat
mvnw.cmd -q test "-Dtest=NPlusOneTest,BatchInsertTest"
mvnw.cmd test "-Dtest=BatchInsertTest" | findstr "Executing JDBC batch"
mvnw.cmd test "-Dtest=NPlusOneTest#naiveImplementationRunsNPlusOneQueries" | find /c "from order_line"
mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

### 13.4 H2 console (both systems)

```text
 browser ──► http://localhost:8080/h2-console
             JDBC URL: jdbc:h2:mem:shop     User: sa     Password: (empty)
             SELECT * FROM flyway_schema_history;      → 2 rows
             EXPLAIN SELECT ... ;                      → copy the plan into README.md
 stop the app: Ctrl + C in the terminal (the in-memory database is gone after that)
```

### 13.5 Expected output (shape)

```text
DEBUG org.hibernate.SQL : select o1_0.id,... from orders o1_0 where o1_0.status=?
DEBUG org.hibernate.SQL : select l1_0.order_id,l1_0.id,... from order_line l1_0 where l1_0.order_id=?
... (49 more for the naive test)
# with batch size 16:
DEBUG org.hibernate.SQL : select l1_0.order_id,... from order_line l1_0 where l1_0.order_id in (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
# batching:
TRACE o.h.e.jdbc.batch.internal.BatchImpl : Executing JDBC batch (50 / 50) - insert into product ...
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0     (e.g. NPlusOneTest 4 + product-name 1 + BatchInsertTest 1)

INFO  o.f.core.internal.command.DbMigrate : Migrating schema "PUBLIC" to version "2 - indexes"
```

### 13.6 Common run problems

| Symptom | Cause / fix |
| --- | --- |
| `cannot find symbol list(...)` / `ordersOf(...)` | the `OrderService` merge problem, section 0 |
| `NULL not allowed for column "ID"` | an entity uses `IDENTITY` but the column is not an identity column (section 0 and 9) |
| naive test shows 5 (or 3), not 51 | `@BatchSize` on `Order.lines` or `default_batch_fetch_size` in `application.yml` is active (sections 6 and 8) |
| counts a bit higher than expected | `counter.reset()` not right before the call, a `findById` after the call, or extra PAID orders from other tests (section 3) |
| counts change from run to run | tests running in parallel; keep `@Execution(SAME_THREAD)` |
| `LazyInitializationException` inside the report | the report method is missing `@Transactional(readOnly = true)` |
| no `LazyInitializationException` in the open-in-view test | the test class is `@Transactional`, or `open-in-view` is `true` |
| `prepareStatementCount` still ≈ 1 000 | `jdbc.batch_size` not set, no flush before the asserts, or the id strategy is `IDENTITY` |
| `Validate failed: Migration checksum mismatch` | V1 was edited; put it back and make a new `V2`/`V3` |
| duplicate `sku` in `BatchInsertTest` | data stays between `@SpringBootTest` tests; use unique SKUs (UUID prefix + counter, max 20 chars) |
| PowerShell: `Unknown lifecycle phase` | a `-D...` argument was split; wrap it in double quotes |

---

## 14. Quick memory card

```text
 N+1                  → 1 query for the list + 1 per element for a lazy association
 QueryCounter         → reset() before, count() right after; exact assert = build fails on regression
 join fetch           → 1 query, you write the join (inner unless "left join fetch"); no paging with it
 @EntityGraph         → 1 query, fetch plan on a repository method, left joins, can go deeper (lines.product)
 @BatchSize(n)        → keep lazy loading, load n collections per query: 1 + ceil(N / n); applies to EVERY path
 default_batch_fetch_size → global cap for every lazy association without its own @BatchSize
 jdbc.batch_size      → many inserts per round trip; needs SEQUENCE ids (IDENTITY disables it)
 order_inserts        → group inserts by table so batches stay long
 open-in-view=false   → no lazy loading after the service returns; map to DTOs inside the service (OrderDto)
 V2__indexes.sql      → never edit V1; new change = new versioned file
 EXPLAIN              → check that the index is used (and "index sorted" for ORDER BY); index must match the query
```

## 15. Acceptance checklist

- [ ] `NPlusOneTest` proves 51 statements for the naive code and 1 / 1 / 5 for join fetch, entity graph and `@BatchSize`.
- [ ] The README table with statement counts and median timings exists, including the "product name" regression and its fix.
- [ ] JDBC batching is enabled and proven by `BatchInsertTest`; the IDENTITY comparison is documented; the open-in-view=false evidence is in the tests.
- [ ] `V2__indexes.sql` is applied by Flyway, V1 is untouched, and both EXPLAIN plans are in the README.
