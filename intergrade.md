## Challenge — layered order management

Extra-credit challenge of **Lab-02: Spring Framework**. The `shop` application from Tasks 1–5 gets **customers and
orders**:

- A customer places an order with several lines.
- Stock is reserved for **all lines or for none**.
- Orders can be listed page by page and filtered.
- A NEW order can be cancelled, which gives the stock back and notifies the customer.

The code is **packaged by feature** and tested at **four levels** (unit, `@JdbcTest`, `@WebMvcTest`, end-to-end).

> Tasks 1–5 come before this section. Every step is tagged in git: `task-0` … `task-5`, `challenge`.

---

### Contents

1. [What the challenge asks for, and where it is](#1-what-the-challenge-asks-for-and-where-it-is)
2. [Technologies and Spring features used](#2-technologies-and-spring-features-used)
3. [Project structure (package by feature)](#3-project-structure-package-by-feature)
4. [Database](#4-database)
5. [REST API](#5-rest-api)
6. [Request flow of "place order"](#6-request-flow-of-place-order)
7. [Transaction boundary — where and why](#7-transaction-boundary--where-and-why)
8. [Audit: `@Audited` + request-scoped bean](#8-audit-audited--request-scoped-bean)
9. [Profiles: dev, test, prod (PostgreSQL)](#9-profiles-dev-test-prod-postgresql)
10. [How to run](#10-how-to-run)
11. [How to run the tests](#11-how-to-run-the-tests)
12. [Notes and known limitations](#12-notes-and-known-limitations)

---

### 1. What the challenge asks for, and where it is

| Requirement | Implementation |
|---|---|
| Tables `customer`, `orders`, `order_line`; status `NEW`, `PAID`, `CANCELLED` | `src/main/resources/schema.sql` (H2), `db/postgresql/schema.sql` (PostgreSQL), `order/OrderStatus` |
| Repositories with `JdbcClient`, package by feature | `product/ProductRepository`, `customer/CustomerRepository`, `order/OrderRepository` |
| `POST /api/orders`: validate, decrement stock in **one** `@Transactional` method, snapshot price, total with tax, 201 | `order/PlaceOrder` (validation), `order/OrderService.place`, `order/OrderPricing`, `order/OrderController` |
| Not enough stock on any line → 409 and **nothing changed** | guarded `UPDATE` + rollback; proven by `OrderFlowE2ETest` |
| `GET /api/orders?customerId=&status=&page=&size=` with `LIMIT/OFFSET`, `GET /api/orders/{id}` | `OrderRepository.findPage` / `count`, `common/Page` |
| `POST /api/orders/{id}/cancel`: only NEW, returns stock, notifies; otherwise 409 | `OrderService.cancel` + `notification/Notifier` from Task 2 |
| `@Audited` + aspect (who / what / when) with a request-scoped bean holding the request id | `audit/Audited`, `audit/AuditAspect`, `audit/RequestContext` |
| `test` profile with H2 and its own `data-test.sql` | `src/test/resources/application-test.yml`, `data-test.sql` |
| `prod` profile on PostgreSQL, `docker-compose.yml`, driver as runtime dependency | `application-prod.yml`, `docker-compose.yml`, `pom.xml` |
| Tests: pricing unit test, `@JdbcTest`, `@WebMvcTest`, one `RANDOM_PORT` end-to-end test | see [section 11](#11-how-to-run-the-tests) |
| Report: package diagram, request flow, transaction boundary | sections 3, 6 and 7 below |

---

### 2. Technologies and Spring features used

#### Stack

| | Version | Used for |
|---|---|---|
| Java | 25 | records, text blocks, `List.getFirst()` |
| Spring Boot | 4.1.1 | auto-configuration, embedded Tomcat, profiles, Actuator |
| Spring Framework | 7.0 | Web MVC, `JdbcClient`, transactions, AOP |
| Maven (wrapper `./mvnw`) | 3.10 | build and tests; no local Maven needed |
| H2 | 2.4.240 | in-memory database for `dev` and `test` |
| PostgreSQL + Docker Compose | 17 (`postgres:17-alpine`), driver 42.7 | database for `prod` |
| Hibernate Validator (Bean Validation) | 9.1 | `@NotNull`, `@NotEmpty`, `@Min`, `@Max` on requests |
| AspectJ (`spring-boot-starter-aspectj`) | 1.9 | `@Aspect` audit and timing advice |
| Jackson | 3.1 | JSON (records, `OffsetDateTime`) |
| JUnit Jupiter / Mockito / AssertJ | 6.0 / 5.23 / 3.27 | tests |
| MockMvc, `RestTestClient` | Spring Test 7 | controller slice tests, real-HTTP end-to-end test |

#### Spring concepts applied in the challenge

| Concept | Where |
|---|---|
| Layered architecture **controller → service → repository**, package by feature | `product/`, `customer/`, `order/` |
| Constructor injection, `final` fields, no field injection | every bean |
| `JdbcClient` with **named, bound parameters** (no SQL string building from input) | all repositories |
| Declarative transactions: `@Transactional`, `@Transactional(readOnly = true)` | `OrderService`, `ProductService`, `CustomerService` |
| Atomic **guarded updates** (`… WHERE stock - :qty >= 0`, `… WHERE status = 'NEW'`) | `ProductRepository.updateStock`, `OrderRepository.updateStatus` |
| Bean Validation on request records, including nested lists (`@Valid List<@NotNull Line>`) | `order/PlaceOrder` |
| Method validation on request parameters (`@Min(0) page`, `@Max(100) size`) | `OrderController.list` |
| One `@RestControllerAdvice` returning RFC 9457 **`ProblemDetail`** | `web/ApiExceptionHandler` + `common/*Exception` |
| AOP: custom annotation + `@Around` advice, ordered outside the transaction | `audit/AuditAspect`, `monitoring/TimingAspect` |
| **Request scope** (`@RequestScope`) injected into a singleton through a scoped proxy | `audit/RequestContext` → `AuditAspect` |
| Servlet filter + MDC for a request id in every log line | `web/RequestIdFilter` |
| Type-safe, validated configuration (`@ConfigurationProperties` record) | `config/ShopProperties` (`taxRate`, `currency`) |
| A `Clock` bean, so time is injectable and testable | `config/AppConfig`, used by `OrderService` and `AuditAspect` |
| Profiles and SQL init (`spring.sql.init.*`) | `application-{dev,test,prod}.yml` |
| Test slices and full tests: `@JdbcTest`, `@WebMvcTest` + `@MockitoBean`, `@SpringBootTest(RANDOM_PORT)` | `src/test/java` |

---

### 3. Project structure (package by feature)

Each feature package holds its own **web, service and repository** classes. Cross-cutting and shared code lives in a few small packages.

```
src/main/java/edu/itc/shop
├── ShopApplication.java, ServletInitializer.java, StartupReport.java
├── order/          the challenge
│   ├── OrderController          web      POST /api/orders, GET list / by id, POST /{id}/cancel
│   ├── OrderService             service  place(), cancel(), list(), findById()  — @Transactional @Audited
│   ├── OrderRepository          repo     JdbcClient: insert, findById, findPage (LIMIT/OFFSET), count, updateStatus
│   ├── OrderPricing             pure     net and total with tax (no Spring)
│   ├── PlaceOrder, OrderDto     request / response records
│   ├── Order, OrderLine, OrderStatus
│   └── OrderNotFoundException, OrderNotCancellableException
├── customer/       CustomerController → CustomerService → CustomerRepository, Customer, CustomerNotFoundException
├── product/        ProductController, ProductPageController (JSP) → ProductService → ProductRepository, …
├── notification/   Notifier + EmailNotifier / SmsNotifier / ConsoleNotifier (Task 2), WelcomeService, ReportBuilder
├── audit/          @Audited, AuditAspect, RequestContext (@RequestScope)
├── monitoring/     TimingAspect
├── common/         NotFoundException (→404), ConflictException (→409), InvalidRequestException (→400), Page<T>
├── web/            ApiExceptionHandler, RequestIdFilter, HelloController       (shared web infrastructure)
└── config/         AppConfig (Clock bean), ShopProperties (shop.currency, shop.tax-rate, …)
```

```mermaid
flowchart LR
  subgraph web_shared["web (shared)"]
    RequestIdFilter
    ApiExceptionHandler
  end
  subgraph order
    OrderController --> OrderService
    OrderService --> OrderPricing
    OrderService --> OrderRepository
  end
  subgraph product
    ProductController --> ProductService --> ProductRepository
    ProductPageController --> ProductService
  end
  subgraph customer
    CustomerController --> CustomerService --> CustomerRepository
  end
  subgraph cross["cross-cutting"]
    AuditAspect --> RequestContext
    TimingAspect
  end
  OrderService --> ProductRepository
  OrderService --> CustomerRepository
  OrderService --> Notifier["notification.Notifier"]
  OrderService --> ShopProperties["config.ShopProperties"]
  ApiExceptionHandler -. "maps NotFound / Conflict / InvalidRequest" .-> order & product & customer
  AuditAspect -. "@Audited" .-> OrderService & ProductService
  OrderRepository & ProductRepository & CustomerRepository --> DB[("H2 (dev, test)<br/>PostgreSQL (prod)")]
```

The dependencies go one way: controller → service → repository → database. `OrderService` uses `ProductRepository` (as in the lab's skeleton) and `CustomerRepository`. `ApiExceptionHandler` only knows the three base exceptions in `common/`, so a new feature does not have to change it.

---

### 4. Database

```
customer   (id PK, name, email UNIQUE)
product    (id PK, name UNIQUE, price DECIMAL(10,2), stock INT CHECK (stock >= 0))
orders     (id PK, customer_id FK → customer, status NEW|PAID|CANCELLED, placed_at TIMESTAMP WITH TIME ZONE, total DECIMAL(12,2))
order_line (order_id FK → orders, product_id FK → product, qty CHECK (qty >= 1), unit_price DECIMAL(10,2), PK (order_id, product_id))
```

- `order_line.unit_price` is a **snapshot** of the price at order time, so later price changes never change old orders.
- Because of the foreign key, a product that is part of an order cannot be deleted (`DELETE /api/products/3` → 409).
- `orders.total` = Σ(unit price × qty) × (1 + `shop.tax-rate`), rounded `HALF_UP` to cents **once, on the total**.
  Example with tax 10 %: 2 × 49.50 + 1 × 9.95 = 108.95 → × 1.10 = 119.845 → **119.85**.

---

### 5. REST API

| Method and path | Success | Errors |
|---|---|---|
| `POST /api/orders` `{"customerId":1,"lines":[{"productId":3,"qty":2}]}` | **201**, `Location: /api/orders/{id}`, the order | 400 invalid body / same product twice · 404 unknown customer or product · **409 not enough stock (nothing changed)** |
| `GET /api/orders?customerId=1&status=NEW&page=0&size=20` | 200 `{content, page, size, totalElements, totalPages}`, newest first | 400 `size` > 100 or `page` < 0, unknown status |
| `GET /api/orders/{id}` | 200, order with lines | 404 |
| `POST /api/orders/{id}/cancel` | 200, status `CANCELLED`, stock returned, customer notified | 404 · **409 if not NEW** |
| `GET /api/customers`, `GET /api/customers/{id}` | 200 | 404 |

Every error is `application/problem+json`, for example:

```console
$ curl -i -X POST localhost:8080/api/orders -H 'Content-Type: application/json' \
       -d '{"customerId":1,"lines":[{"productId":3,"qty":2},{"productId":5,"qty":1}]}'
HTTP/1.1 201
Location: /api/orders/3
{"id":3,"customerId":1,"status":"NEW","placedAt":"2026-10-29T09:06:34.640146Z","total":119.85,
 "lines":[{"productId":3,"qty":2,"unitPrice":49.50},{"productId":5,"qty":1,"unitPrice":9.95}]}

$ curl -i -X POST localhost:8080/api/orders -H 'Content-Type: application/json' \
       -d '{"customerId":1,"lines":[{"productId":3,"qty":2},{"productId":2,"qty":999}]}'
HTTP/1.1 409
Content-Type: application/problem+json
{"detail":"Product 2 has 150 units, cannot remove 999","instance":"/api/orders","status":409,"title":"Conflict"}
# product 3 still has 78 units: the decrement of the first line was rolled back
```

The full recorded session (16 calls, including every error case) is in
`shop/api-session.md` (section *Orders*).

---

### 6. Request flow of "place order"

```mermaid
sequenceDiagram
  autonumber
  participant C as Client
  participant F as RequestIdFilter
  participant OC as OrderController
  participant AU as AuditAspect
  participant TX as TransactionInterceptor
  participant OS as OrderService.place
  participant CR as CustomerRepository
  participant PR as ProductRepository
  participant OR as OrderRepository
  participant DB as H2 / PostgreSQL
  C->>F: POST /api/orders {customerId, lines}
  F->>F: requestId → MDC, request attribute, X-Request-Id header
  F->>OC: DispatcherServlet: JSON → PlaceOrder, @Valid (400 on field errors)
  OC->>AU: service.place(cmd) — call goes through the Spring proxy
  AU->>AU: who / what / when from the request-scoped RequestContext
  AU->>TX: proceed()
  TX->>DB: take connection, setAutoCommit(false), bind it to the thread
  TX->>OS: proceed() (TimingAspect also wraps this call)
  OS->>CR: findById(customerId) — 404 if missing
  OS->>OS: reject duplicate productIds (400)
  loop every line
    OS->>PR: UPDATE product SET stock = stock - qty WHERE id = ? AND stock - qty >= 0
    alt 0 rows updated
      OS-->>TX: throw InsufficientStockException
      TX->>DB: ROLLBACK (earlier decrements are undone)
      TX-->>AU: exception
      AU-->>OC: AUDIT … outcome=FAILED, rethrow
      OC-->>C: 409 application/problem+json (ApiExceptionHandler)
    end
    OS->>PR: SELECT … → unit price snapshot
  end
  OS->>OS: OrderPricing.total(lines, taxRate)
  OS->>OR: INSERT orders + INSERT order_line × n
  OS-->>TX: OrderDto
  TX->>DB: COMMIT, release connection
  TX-->>AU: OrderDto
  AU-->>OC: AUDIT … outcome=OK result=OrderDto(id=3)
  OC-->>C: 201 Location: /api/orders/3
```

---

### 7. Transaction boundary — where and why

**Where:** on the public **service** methods. `OrderService.place` and `OrderService.cancel` are each one `@Transactional` unit of work.

**How it works:**

- The `TransactionInterceptor` in the Spring proxy opens one transaction before the method runs: one JDBC connection with `autoCommit=false`, bound to the thread.
- Every `JdbcClient` call inside the method uses that same connection.
- The interceptor **commits** when the method returns and **rolls back** when a `RuntimeException` leaves it.

So if line 2 has too little stock, the decrement of line 1 is undone and no order row is written.

**Why not elsewhere:**

- **Not in the repositories.** Each statement would commit on its own, and a failure in line 2 would leave line 1's stock decremented. That is exactly the partial update the use case forbids.
- **Not in the controller.** The transaction would also cover JSON binding, validation and response writing, holding a connection longer than needed. The controller is also not the only caller: the JSP page controller, a batch job or a test can call the service and should get the same atomicity.

**Supporting decisions:**

- **Guarded updates instead of read-then-write.** `UPDATE … SET stock = stock - :qty WHERE id = :id AND stock - :qty >= 0` checks and changes the stock in one statement, so two concurrent orders cannot oversell. `UPDATE orders SET status = 'CANCELLED' WHERE id = :id AND status = 'NEW'` lets only one of two simultaneous cancels win, so stock is never returned twice.
- **`AuditAspect` runs outside the transaction** (`@Order(HIGHEST_PRECEDENCE)`), so `outcome=OK` is only logged after the commit succeeded.
- **The notification in `cancel` is the last step.** Any failure before it rolls back without notifying anyone. In production it would be sent from an `@TransactionalEventListener(phase = AFTER_COMMIT)`, so a failed commit can never produce a wrong message.
- **`list` is `@Transactional(readOnly = true)`.** `COUNT(*)` and the page query then run on the same connection.
- **Proxy caveat.** `@Transactional` and `@Audited` only apply when the method is called from another bean, through the proxy. A self-call (`this.place(…)`) would bypass both.

---

### 8. Audit: `@Audited` + request-scoped bean

- `audit/RequestContext` is a `@RequestScope` bean, one instance per HTTP request. It holds the **request id** (set by `RequestIdFilter`) and **who** is calling: the `X-User` header, otherwise `anonymous@<ip>`. There is no login in this lab; a real application would use the Spring Security principal.
- `audit/AuditAspect` is a singleton. It receives a **scoped proxy** of `RequestContext`, which always forwards to the current request's instance. Outside a request (tests, start-up) it logs `who=system request=-`.
- `@Around("@annotation(edu.itc.shop.audit.Audited)")` writes one line per call: who, what (method and arguments), when, outcome and duration. Failures are logged at WARN.
- `@Audited` is on `OrderService.place` / `cancel` and on the methods of `ProductService` that change data.

```
16:06:34.650 INFO  [46e11e3a] edu.itc.shop.audit.AuditAspect : AUDIT who=sokha what=OrderService.place(PlaceOrder[customerId=1, lines=[Line[productId=3, qty=2], Line[productId=5, qty=1]]]) when=2026-10-29T09:06:34.588831605Z outcome=OK result=OrderDto(id=3) (61 ms) request=46e11e3a
16:06:34.748 WARN  [30921966] edu.itc.shop.audit.AuditAspect : AUDIT who=anonymous@0:0:0:0:0:0:0:1 what=OrderService.place(PlaceOrder[customerId=1, lines=[Line[productId=3, qty=2], Line[productId=2, qty=999]]]) when=2026-10-29T09:06:34.742126938Z outcome=FAILED error=InsufficientStockException: Product 2 has 150 units, cannot remove 999 (5 ms) request=30921966
16:06:34.990 INFO  [c719a93c] edu.itc.shop.audit.AuditAspect : AUDIT who=sokha what=OrderService.cancel(3) when=2026-10-29T09:06:34.978555348Z outcome=OK result=OrderDto(id=3) (11 ms) request=c719a93c
```

---

### 9. Profiles: dev, test, prod (PostgreSQL)

| Profile | Database | SQL scripts |
|---|---|---|
| default / `dev` | H2 in memory, `jdbc:h2:mem:shop` (H2 console at `/h2-console` in `dev`) | `schema.sql` + `data.sql`: 5 products, 3 customers, 2 orders |
| `test` | H2, **a new database for every Spring test context** (`jdbc:h2:mem:test-${random.uuid}`) | `schema.sql` + **`data-test.sql`**: fixed ids and numbers the tests rely on |
| `prod` | **PostgreSQL**. `driver-class-name: org.postgresql.Driver`; URL, user and password **only from environment variables** `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | `db/postgresql/schema.sql` (`CREATE TABLE IF NOT EXISTS`) + `db/postgresql/data.sql` (`ON CONFLICT DO NOTHING`): safe to run on every start |

Settings used by the orders (`application.yml`):

```yaml
shop:
  currency: USD
  tax-rate: 0.10           # validated at start-up: 0.0 … 1.0
  low-stock-threshold: 20
```

---

### 10. How to run

All commands run from the `shop/` folder. Only JDK 25 is needed; `./mvnw` downloads Maven.

#### Development (H2, no setup)

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Then try it:

```bash
curl localhost:8080/api/customers
curl -i -X POST localhost:8080/api/orders -H 'Content-Type: application/json' -H 'X-User: sokha' \
     -d '{"customerId":1,"lines":[{"productId":3,"qty":2},{"productId":5,"qty":1}]}'
curl 'localhost:8080/api/orders?customerId=1&page=0&size=5'
curl -X POST localhost:8080/api/orders/3/cancel
curl localhost:8080/api/products/3          # stock is back
```

The JSP product pages are at <http://localhost:8080/products> and the H2 console at <http://localhost:8080/h2-console> (JDBC URL `jdbc:h2:mem:shop`, user `sa`, empty password).

#### Production profile with PostgreSQL (Docker)

```bash
export DB_PASSWORD=$(openssl rand -hex 16)       # never committed; compose refuses to start without it
docker compose up -d                              # PostgreSQL 17 on localhost:5432
docker compose ps                                 # wait until the service shows (healthy)

DB_URL=jdbc:postgresql://localhost:5432/shop DB_USERNAME=shop \
  ./mvnw spring-boot:run -Dspring-boot.run.profiles=prod

docker compose down                               # stop; add -v to delete the data volume as well
```

---

### 11. How to run the tests

#### All tests

```bash
./mvnw test             # compile and run all 61 tests
./mvnw verify           # same, plus packaging the war (what the lab asks for: 0 failures)
./mvnw clean verify     # from scratch; use this after renaming or moving classes
```

Expected result:

```
[INFO] Tests run: 61, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

#### Only some tests

| What | Command | Tests |
|---|---|---|
| Unit tests of the order logic (no Spring) | `./mvnw test -Dtest='OrderPricingTest,OrderServiceTest'` | 11 |
| Repository slice tests (`@JdbcTest`) | `./mvnw test -Dtest='*RepositoryTest'` | 15 |
| Controller slice tests (`@WebMvcTest`) | `./mvnw test -Dtest='*ControllerTest'` | 20 |
| End-to-end test (real HTTP, random port) | `./mvnw test -Dtest=OrderFlowE2ETest` | 4 |
| Everything in the `order` package | `./mvnw test -Dtest='edu.itc.shop.order.**'` | 27 |
| One single test method | `./mvnw test -Dtest='OrderFlowE2ETest#lineWithoutEnoughStockIs409AndNothingChanges'` | 1 |

Spring tests run with `@ActiveProfiles("test")`, so they use H2 and `data-test.sql`. No database or Docker is needed. Results are written to `target/surefire-reports/`.

#### Tests at the four levels

| Level | Classes | What they prove |
|---|---|---|
| **Unit, no Spring** (18) | `OrderPricingTest`, `OrderServiceTest` (Mockito), plus `WelcomeServiceTest`, `LowStockHealthIndicatorTest`, `RequestIdFilterTest`, `ShopPropertiesTest` from Tasks 2–5 | net and total, tax, rounding once on the total; duplicate product, unknown customer, not enough stock; only NEW can be cancelled, stock returned, notification sent |
| **`@JdbcTest`** (15) | `OrderRepositoryTest`, `CustomerRepositoryTest`, `ProductRepositoryTest` | insert + read back with lines; `LIMIT/OFFSET` pages newest first; filter by customer/status; count; conditional status update; foreign key on delete |
| **`@WebMvcTest`** (20) | `OrderControllerTest`, `CustomerControllerTest`, `ProductControllerTest`, `ProductPageControllerTest` | 201 + `Location`; 400 field errors (body and `size` parameter); 404/409 problem details; page JSON |
| **`@SpringBootTest`** (8) | **`OrderFlowE2ETest`** (`RANDOM_PORT` + `RestTestClient`), `ProductServiceTest`, `ShopApplicationTests` | **place → stock down → cancel → stock back → second cancel 409**; **409 order leaves stock and order count unchanged**; paging over HTTP; AUDIT line contains the user and the request id |

---

### 12. Notes and known limitations

- **PostgreSQL was not run on the development machine** (no Docker installed). The `prod` profile was smoke-tested on H2 in PostgreSQL mode with a file database, started twice: the scripts are re-runnable, orders were stored and stock carried over the restart. Run the commands in [section 10](#production-profile-with-postgresql-docker) for the real check. Two PostgreSQL details are already handled:
  - `insert` names the key column (`update(keys, "id")`), because the PostgreSQL driver otherwise returns all columns as keys.
  - `placed_at` is written as UTC `OffsetDateTime` with microsecond precision.
- **H2 pitfall.** In H2 2.4, `CHECK (status IN ('NEW','PAID','CANCELLED'))` stops working once the connection that created the table is closed. Every insert then fails with *Check constraint invalid … database has been closed*. The H2 schema therefore uses an equivalent `CASE` check; the PostgreSQL schema keeps `IN`.
- **No authentication.** "Who" in the audit log comes from an `X-User` header. With Spring Security it would be the logged-in user, and `/actuator` would be protected too.
- **Executable war in a path with spaces.** JSP pages fail when `java -jar …war` is started from a folder whose path contains a space. Use `./mvnw spring-boot:run`, or copy the war to a path without spaces. The REST API is not affected. Details are in Task 5 (JSP pages).
