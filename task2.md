# Task 2 — Associations, cascading and entity identity

A concept note with **run diagrams**: what happens in Java, in Hibernate and in the database
at each step of Task 2. It builds on [task1.md](task1.md) (same Flyway schema, same `@DataJpaTest` setup).
No new migration is needed: `orders`, `order_line`, `tag` and `product_tag` already exist in `V1__init.sql`.

---

## 0. The whole picture

### Java objects

```text
             ┌──────────┐ 1      * ┌──────────────┐ *      1 ┌───────────┐ *     * ┌─────────┐
             │ Customer │◄─────────│    Order     │          │  Product  │◄───────►│   Tag   │
             └──────────┘ customer │ (aggregate   │          │           │  tags / │         │
                                   │   root)      │          │           │ products│         │
                                   └──────┬───────┘          └─────▲─────┘         └─────────┘
                                   lines  │ 1                        │ product
                                          │                          │
                                          ▼ *                        │
                                   ┌──────────────┐                  │
                                   │  OrderLine   │──────────────────┘
                                   │ quantity     │
                                   │ unitPrice    │ (Money: amount + currency)
                                   └──────────────┘
```

### Database tables (created by Flyway)

```text
 customer            orders                 order_line                 product            product_tag         tag
┌────────┐        ┌────────────┐        ┌────────────────┐         ┌─────────┐        ┌────────────┐     ┌──────┐
│ id  PK │◄───────│ customer_id│        │ id          PK │         │ id   PK │◄───────│ product_id │     │id PK │
│ name   │        │ id      PK │◄───────│ order_id    FK │         │ sku     │        │ tag_id     │────►│ name │
│ email  │        │ status     │        │ product_id  FK │────────►│ price   │        └────────────┘     └──────┘
└────────┘        │ placed_at  │        │ quantity       │         └─────────┘
                  │ version    │        │ amount,currency│
                  └────────────┘        └────────────────┘
```

Key point: **the database has one foreign key per relationship**, but Java has a field on **both** sides.
Task 2 is about making these two views agree.

---

## 1. Concept: owning side vs inverse side

```text
        JAVA (two references)                          DATABASE (one column)

   Order.lines  ──────────►  [line1, line2]
   (INVERSE, mappedBy="order")   ✗ never written to DB
                                                       order_line.order_id
   OrderLine.order ──────────► order  ─────────────►   ✓ written from HERE
   (OWNER, @ManyToOne @JoinColumn("order_id"))
```

```java
// OrderLine = OWNER: Hibernate reads this field to fill order_line.order_id
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "order_id", nullable = false)
private Order order;

// Order = INVERSE: "the mapping is on OrderLine.order"
@OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
private List<OrderLine> lines = new ArrayList<>();
```

Rule: **Hibernate only looks at the owner when it writes the foreign key.**
If only `order.lines` is set, `order_id` is `NULL` → insert fails.
So Java must keep **both** sides in sync → that is the job of the helper methods.

---

## 2. Concept: helper methods keep both sides in sync

### `addLine(product, qty)`

```text
order.addLine(keyboard, 2)
   │
   ├─1─► new OrderLine(order=this, product=keyboard, quantity=2, unitPrice=keyboard.getPrice())
   │          │                                                   │
   │          └─ sets OWNER side (line.order = order)             └─ price SNAPSHOT (copied now)
   │
   ├─2─► lines.add(line)          ← sets INVERSE side
   │
   └─3─► return line

  After:   order.lines = [line]    and    line.order = order     ✓ both sides agree
```

### `removeLine(line)`

```text
order.removeLine(line)
   │
   ├─1─► lines.remove(line)       ← uses equals() (same id) to find it
   └─2─► line.setOrder(null)      ← unlink the owner side too

  After:   order.lines = [ ]      and    line.order = null      → line is now an ORPHAN
```

### `total()`

```text
lines:   [ 89.90 × 2 ]   [ 25.00 × 1 ]
              │               │
         times(2)=179.80  times(1)=25.00
              └──── plus ─────┘
     start Money.eur("0") ─► 0 + 179.80 + 25.00 = 204.80 EUR
```

### Protection

```text
 order.getLines().add(x)   ──►  UnsupportedOperationException   (unmodifiableList)
 new OrderLine(...)        ──►  only inside package (package-private constructor)
 line.setOrder(...)        ──►  only inside package
                                => the ONLY way to change lines is through Order
```

---

## 3. Concept: cascade and orphan removal (Order = aggregate root)

```text
   cascade = ALL  =  PERSIST + MERGE + REMOVE + REFRESH + DETACH
                       │                 │
                       ▼                 ▼
          persist(order) also       remove(order) also
          persists its lines        deletes its lines

   orphanRemoval = true
          a line taken OUT of order.lines  ──►  DELETE that row
```

### Run diagram A — persist an order with 2 lines

```text
 TEST CODE                       PERSISTENCE CONTEXT (1st-level cache)       DATABASE (H2)
 ─────────                       ──────────────────────────────────          ─────────────
 order = new Order(customer)
 order.addLine(keyboard, 2)
 order.addLine(mouse, 1)
                                  (nothing yet — objects are NEW/transient)

 em.persist(order) ───────────►  order  : MANAGED, id assigned  ───────────► select next value for orders_seq
          │ cascade PERSIST        line1  : MANAGED, id assigned  ───────────► select next value for order_line_seq
          └──────────────────►    line2  : MANAGED, id assigned       (1 call gives 50 ids: allocationSize = 50)

 em.flush() ──────────────────►  write queue, parent first:
                                   1. INSERT order  ──────────────────────────► insert into orders (...)
                                   2. INSERT line1 (order_id known!) ─────────► insert into order_line (...)
                                   3. INSERT line2 (order_id known!) ─────────► insert into order_line (...)

                                  Result: 1 + N inserts, 0 updates
                                  (no update because order_id was known at insert time)

 em.clear() ──────────────────►  cache emptied (all objects DETACHED)

 em.find(Order.class, id) ────►  not in cache ───────────────────────────────► select ... from orders where id=?
 order.getLines() ────────────►  lazy collection loads ──────────────────────► select ... from order_line where order_id=?

 assert lines.size() == 2   ✓
 assert total() == 204.80   ✓   (2 × 89.90 + 1 × 25.00)
```

### Run diagram B — remove a line (orphan removal)

```text
 TEST CODE                        HIBERNATE                                   DATABASE
 ─────────                        ─────────                                   ────────
 order = em.find(...)  ─────────► load order + lines                          order_line: [L1, L2]
 order.removeLine(lines.get(0))
       lines.remove(L1)
       L1.setOrder(null)
                                  at flush: "L1 was in the collection,
                                  now it is not"  → L1 is an ORPHAN
 em.flush() ────────────────────► schedule DELETE L1 ────────────────────────► delete from order_line where id=?
                                                                              order_line: [L2]

 select count(l) from OrderLine l:   2  ──►  1   ✓
 SQL log contains "delete from order_line"   ✓
```

Without `orphanRemoval = true`: Hibernate would only try to set `order_id = NULL`
(not allowed: column is `NOT NULL`) and the row would **not** be deleted.

> Note: changing `order.lines` also counts as changing the order, so Hibernate may bump
> `orders.version` (`update orders set version=? ...`). That is `@Version` optimistic locking, not an error.

### Run diagram C — remove the whole order (cascade REMOVE)

```text
 em.remove(order) ──► cascade REMOVE to every line still in order.lines
 em.flush()       ──► children first, then parent:
                        delete from order_line where id=?     (L2)
                        delete from orders where id=? and version=?

 count(OrderLine) = 0   ✓      em.find(Order.class, id) == null   ✓
```

---

## 4. Concept: many-to-many Product ↔ Tag

```text
   Product.tags  (OWNER)                    Tag.products (INVERSE, mappedBy = "tags")
   @ManyToMany
   @JoinTable(name = "product_tag",
     joinColumns        = product_id,
     inverseJoinColumns = tag_id)
          │
          └──── Hibernate writes product_tag ONLY from Product.tags
```

### Run diagram — tagging

```text
 TEST CODE                          JAVA MEMORY                              DATABASE
 ─────────                          ───────────                              ────────
 em.persist(tagGaming)   ─────────► tag MANAGED  ──────(flush)─────────────► insert into tag ...
 keyboard.tag(tagGaming)
     ├─ keyboard.tags.add(tag)      keyboard.tags   = {gaming}   (OWNER)
     └─ tag.addProduct(keyboard)    tag.products    = {keyboard} (INVERSE, Java only)
 mouse.tag(tagGaming)               tag.products    = {keyboard, mouse}

 em.flush() ──────────────────────► read OWNER collections ─────────────────► insert into product_tag (product_id, tag_id)
                                                                              insert into product_tag (product_id, tag_id)
```

Why these choices:

| Choice | Reason |
| --- | --- |
| `Set`, not `List` | a tag is on a product or not (no duplicates); with a `List` (bag) Hibernate deletes **all** join rows and re-inserts them on every change |
| no `cascade` | tags are shared; `REMOVE` would delete a tag used by other products → persist the `Tag` **first** |
| `Tag.addProduct` package-private | only `Product.tag()` / `untag()` can change it, so the two sides never disagree |

---

## 5. Concept: entity identity (`equals` / `hashCode`)

```java
@Override public boolean equals(Object o) {
    return this == o || (o instanceof Product other && id != null && id.equals(other.getId()));
}
@Override public int hashCode() { return Product.class.hashCode(); }  // constant per class
```

### The problem: the id appears LATER

```text
 time ─────────────────────────────────────────────────────────────►
   new Product(...)          set.add(p)            persist + flush
   id = null                 stored in bucket      id = 1
                             of hashCode()
```

### Bad version: `hashCode()` uses the id

```text
  set.add(p)           hash(null) = 0   →  stored in bucket 0
  persist + flush      id = 1
  set.contains(p)      hash(1)  = 32    →  looks in bucket 32  →  empty  →  FALSE  ✗  (object "lost")
```

### Good version: constant `hashCode()`

```text
  set.add(p)           hash = C   →  bucket C
  persist + flush      id = 1     (hash is still C)
  set.contains(p)      hash = C   →  bucket C  →  equals(p, p): this == o  →  TRUE  ✓
```

### `equals` vs `==` across persistence contexts

```text
 Context 1:   p1 = persist(...)            p1 ──► object @A   (id = 1)
 em.clear()   (context 1 forgotten)
 Context 2:   p2 = em.find(Product, 1)     p2 ──► object @B   (id = 1)   new Java object!

   p1 == p2        →  false   (two different objects in memory)
   p1.equals(p2)   →  true    (same class, same non-null id = same DB row)
```

| Rule in `equals` | Why |
| --- | --- |
| `id != null` | two new (unsaved) objects are never equal, unless it is the same instance |
| `instanceof` | Hibernate lazy **proxies** are subclasses → still match |
| `other.getId()` not `other.id` | a proxy's fields are empty; its getter loads the real value |
| no Lombok `@Data` / `@EqualsAndHashCode` | they use all fields, including associations → slow, can recurse |

---

## 6. Concept: `toString` with scalar fields only

```java
@Override public String toString() { return "Order[id=%d, status=%s]".formatted(id, status); }
```

### Pitfall 1 — StackOverflowError (both sides print each other)

```text
 order.toString()
   └─► prints lines ─► line.toString()
                         └─► prints order ─► order.toString()
                                               └─► prints lines ─► line.toString() ─► ...
                                                                    forever ─► StackOverflowError
```

### Pitfall 2 — LazyInitializationException (session already closed)

```text
 transaction:  order = find(...)      lines = LAZY proxy (not loaded yet)
 transaction ends  → session closed
 System.out.println(order)
   └─► toString() touches lines ─► needs SQL ─► no session!
        LazyInitializationException: Cannot lazily initialize collection of role
        'edu.itc.shop.domain.Order.lines' ... (no session)
```

Rule: printing an entity must **never run SQL and never recurse** → print `id` and simple fields only.
(Reproduce once, write the exception in `README.md`, then revert.)

---

## 7. How a test run works (end to end)

```text
 ./mvnw test -Dtest=OrderCascadeTest,...
        │
        ▼
 Maven Surefire starts JUnit 5
        │
        ▼
 @DataJpaTest  → starts ONLY the JPA part of Spring (entities, EntityManager, repositories)
 @AutoConfigureTestDatabase(replace = NONE)
        │        → do NOT swap the DB; use application.yml: jdbc:h2:mem:shop
        ▼
 Flyway runs V1__init.sql  ──►  tables + sequences created in H2
        │
        ▼
 Hibernate ddl-auto: validate  ──►  entities must match the tables, otherwise start-up fails
        │
        ▼
 for each @Test method:
   ┌─────────────────────────────────────────────────────────────┐
   │ BEGIN transaction  (automatic with @DataJpaTest)             │
   │   @BeforeEach: persist customer + keyboard(89.90) + mouse(25)│
   │                statistics.clear()  ← count only test's SQL   │
   │   test body:   persist / flush / clear / find / assert       │
   │ ROLLBACK  ← nothing stays in DB, tests don't affect each other│
   └─────────────────────────────────────────────────────────────┘
        │
        ▼
 Tests run: N, Failures: 0, Errors: 0
```

### How the tests check the SQL

```text
 ┌────────────────────────────┐        ┌────────────────────────────────────┐
 │ Hibernate Statistics       │        │ Log output (CapturedOutput)        │
 │ generate_statistics: true  │        │ logging org.hibernate.SQL: DEBUG   │
 │                            │        │                                    │
 │ getEntityInsertCount() = 3 │        │ "insert into orders ..."           │
 │ getEntityUpdateCount() = 0 │        │ "delete from order_line where id=?"│
 │ getEntityDeleteCount() = 1 │        │                                    │
 │  → exact numbers           │        │  → proves the SQL text appears     │
 └────────────────────────────┘        └────────────────────────────────────┘
```

### Why `flush()` and `clear()` matter

```text
 em.persist(x)   → only in memory (cache), NO SQL yet
 em.flush()      → send pending SQL to DB now (still inside the transaction)
 em.clear()      → empty the cache; next find() MUST read from DB
                   (without clear(), find() returns the cached object and the test proves nothing)
```

---

## 8. The tests

| Class | Test | Proves |
| --- | --- | --- |
| `OrderCascadeTest` | persist order with 2 lines | 1 insert into `orders` + 2 into `order_line`, 0 updates; reload → 2 lines, total 204.80 EUR |
| `OrderCascadeTest` | remove a line | count 2 → 1, `delete from order_line where id=?` in the log (orphan removal) |
| `OrderCascadeTest` | remove the order | remaining line deleted too (cascade REMOVE) |
| `EntityIdentityTest` | HashSet before/after persist | `set.contains(product)` still true after the id is assigned |
| `EntityIdentityTest` | reload in fresh context | `equals` true, `==` false |
| `ProductTagTest` (extra) | tag 2 products | rows in `product_tag`; both directions read back |

---

## 9. How to run

The project uses the **Maven Wrapper**, so Maven does not need to be installed: `mvnw` (Linux/macOS)
and `mvnw.cmd` (Windows) download the right Maven version on the first run. You only need **JDK 25**.

```text
                     ┌── Arch Linux ──►  ./mvnw      (bash script)
 SE-LAB-Group3/ ─────┤
                     └── Windows ─────►  .\mvnw.cmd  (PowerShell / cmd)
                                 │
                                 ▼
             downloads Maven (first time only) → compiles → runs JUnit tests on H2
             (no database server to install: H2 runs in memory inside the test)
```

### 9.1 Arch Linux (bash)

#### Arch: one-time setup

```bash
sudo pacman -S jdk-openjdk        # latest OpenJDK (25+)
archlinux-java status             # list installed JDKs
sudo archlinux-java set java-25-openjdk   # only if another JDK is the default
java -version                     # must show 25

cd "SE-LAB-Group3"
chmod +x mvnw                     # only if you get "Permission denied"
```

#### Arch: run

```bash
# Task 2 tests only
./mvnw -q test -Dtest=OrderCascadeTest,EntityIdentityTest

# with the extra many-to-many test
./mvnw -q test -Dtest=OrderCascadeTest,EntityIdentityTest,ProductTagTest

# watch the SQL of one test
./mvnw test -Dtest='OrderCascadeTest#persistingOrderCascadesToLinesWithOnlyInserts' | grep "org.hibernate.SQL"

# everything (Task 1 + Task 2)
./mvnw test

# clean build, then all tests
./mvnw clean test
```

### 9.2 Windows (PowerShell)

#### Windows: one-time setup

```powershell
winget install EclipseAdoptium.Temurin.25.JDK    # or install JDK 25 from adoptium.net
# close and reopen the terminal, then check:
java -version                                    # must show 25
echo $env:JAVA_HOME                              # must point to the JDK 25 folder

# if JAVA_HOME is empty (adjust the path to your install):
setx JAVA_HOME "C:\Program Files\Eclipse Adoptium\jdk-25"
# reopen the terminal after setx

cd "SE-LAB-Group3"
```

#### Windows: run

Put `-D...` arguments in **double quotes**: PowerShell treats `,` and `.` specially.

```powershell
# Task 2 tests only
.\mvnw.cmd -q test "-Dtest=OrderCascadeTest,EntityIdentityTest"

# with the extra many-to-many test
.\mvnw.cmd -q test "-Dtest=OrderCascadeTest,EntityIdentityTest,ProductTagTest"

# watch the SQL of one test
.\mvnw.cmd test "-Dtest=OrderCascadeTest#persistingOrderCascadesToLinesWithOnlyInserts" | Select-String "org.hibernate.SQL"

# everything (Task 1 + Task 2)
.\mvnw.cmd test

# clean build, then all tests
.\mvnw.cmd clean test
```

### 9.3 Windows (cmd.exe)

```bat
mvnw.cmd -q test "-Dtest=OrderCascadeTest,EntityIdentityTest"
mvnw.cmd test "-Dtest=OrderCascadeTest#persistingOrderCascadesToLinesWithOnlyInserts" | findstr "org.hibernate.SQL"
mvnw.cmd test
```

### 9.4 Arch vs Windows at a glance

| Action | Arch Linux (bash) | Windows (PowerShell) |
| --- | --- | --- |
| run wrapper | `./mvnw` | `.\mvnw.cmd` |
| select tests | `-Dtest=A,B` | `"-Dtest=A,B"` (quoted) |
| one test method | `-Dtest='A#method'` | `"-Dtest=A#method"` |
| filter log | `\| grep "org.hibernate.SQL"` | `\| Select-String "org.hibernate.SQL"` |
| install JDK | `pacman -S jdk-openjdk` | `winget install EclipseAdoptium.Temurin.25.JDK` |
| pick JDK | `archlinux-java set ...` | `JAVA_HOME` environment variable |
| test reports | `target/surefire-reports/` | `target\surefire-reports\` |

### 9.5 Common run problems

| Symptom | Fix |
| --- | --- |
| `./mvnw: Permission denied` (Arch) | `chmod +x mvnw` |
| `'.\mvnw.cmd' is not recognized` (Windows) | you are not in the `SE-LAB-Group3` folder; `cd` into it |
| `JAVA_HOME is not set` / `release version 25 not supported` | JDK 25 not installed or not selected; see setup above |
| `No tests matching pattern` | class name typo, or the `-Dtest=` argument is not quoted in PowerShell |
| `Unknown lifecycle phase ".something"` (PowerShell) | PowerShell split a `-D...` argument at a `.` or `,`; wrap the whole argument in double quotes |

Expected output (same on both systems) (column order inside the insert is chosen by Hibernate):

```text
DEBUG org.hibernate.SQL : select next value for orders_seq
DEBUG org.hibernate.SQL : select next value for order_line_seq
DEBUG org.hibernate.SQL : insert into orders (customer_id,placed_at,status,version,id) values (?,?,?,?,?)
DEBUG org.hibernate.SQL : insert into order_line (amount,currency,order_id,product_id,quantity,id) values (?,?,?,?,?,?)
DEBUG org.hibernate.SQL : insert into order_line (amount,currency,order_id,product_id,quantity,id) values (?,?,?,?,?,?)
...
DEBUG org.hibernate.SQL : delete from order_line where id=?
...
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0      (6 with ProductTagTest)
```

---

## 10. Quick memory card

```text
 mappedBy            → "I am the inverse side, the other field owns the FK"
 owner side          → the one Hibernate reads to write the FK / join table
 helpers             → always update BOTH sides in Java
 cascade ALL         → persist/remove the parent = persist/remove the children
 orphanRemoval       → child removed from the collection = row deleted
 Set for @ManyToMany → no duplicates, no "delete all + re-insert"
 equals              → same class + same non-null id
 hashCode            → constant per class (survives id assignment)
 toString            → scalar fields only (no lazy load, no recursion)
 flush / clear       → force SQL now / force the next read to hit the DB
```

## 11. Acceptance checklist

- [ ] `OrderLine.order` is the owning side; `Order.lines` uses `mappedBy`, cascade ALL and orphan removal; helpers keep both sides in sync.
- [ ] Persisting an order produces exactly 1 + N inserts and no updates; removing a line produces one delete (log excerpt in `README.md`).
- [ ] `Product` ↔ `Tag` works through `product_tag`; the collections are `Set`s.
- [ ] `equals`/`hashCode`/`toString` are hand-written on all entities, `EntityIdentityTest` passes, and the `toString` pitfall is documented.
