# Task 1 — Entities and mappings, validated by Flyway

This note explains **what Task 1 builds, how the pieces work together, and how to run it**.
The deliverable is a set of JPA entities plus a Flyway migration that Hibernate checks at start-up.

---

## 1. The big idea

There are two descriptions of the same database, written by hand in two places:

| Description | Where | Who owns it |
|---|---|---|
| The **schema** (tables, columns, sequences) | `db/migration/V1__init.sql` | **Flyway** — it creates the tables |
| The **object model** (entities, fields, annotations) | `edu.itc.shop.domain.*` | **Hibernate** — it reads and writes rows |

Nothing forces these two to agree, so we set `ddl-auto: validate`. At start-up Hibernate compares every
`@Entity` with the real tables and **refuses to start** if they differ. A wrong column name, type or
sequence step is caught when the app boots, not later in production.

```text
 ./mvnw spring-boot:run
        │
        ▼
 Flyway runs V1__init.sql  ──►  tables + sequences exist (flyway_schema_history records it)
        │
        ▼
 Hibernate builds EntityManagerFactory
        │   ddl-auto=validate: compare each @Entity with the real tables
        ├──► all match      ──►  "Started ShopJpaApplication"
        └──► any mismatch   ──►  SchemaManagementException / MappingException, app stops
```

Order matters: Spring Boot makes the `EntityManagerFactory` depend on Flyway, so the schema is always
created **before** Hibernate validates it.

---

## 2. Project layout

```text
src/main/java/edu/itc/shop/domain/
├── Money.java        @Embeddable record (amount, currency) + eur() / times()
├── Address.java      @Embeddable record (street, city, zip)
├── Category.java     enum BOOKS, HARDWARE, TOYS, FOOD
├── OrderStatus.java  enum NEW, PAID, SHIPPED, CANCELLED
├── Customer.java     @Entity  -> table customer
├── Product.java      @Entity  -> table product
├── Order.java        @Entity  -> table orders      ("order" is a reserved SQL word)
└── OrderLine.java    @Entity  -> table order_line
src/main/resources/
├── application.yml
└── db/migration/V1__init.sql
src/test/java/edu/itc/shop/domain/ProductMappingTest.java
```

---

## 3. Concepts, one by one

### 3.1 Embedded value objects (`Money`, `Address`)

A value object has no identity of its own and no table. Its fields are stored as **extra columns in
the owner's table**.

```java
@Embeddable
public record Money(
    @Column(name = "amount", nullable = false, precision = 12, scale = 2) BigDecimal amount,
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "currency", nullable = false, length = 3) String currency) { }
```

`Product.price` is a `Money`, so the `product` table gets `amount` and `currency` columns.
`OrderLine.unitPrice` is also a `Money`, so `order_line` gets the same two columns.

**Reusing one embeddable twice in one entity** needs renaming, otherwise both would claim the same
column names. `Customer` has `billing` and `shipping`, both `Address`:

```java
@Embedded private Address billing;                 // street, city, zip

@Embedded
@AttributeOverrides({
    @AttributeOverride(name = "street", column = @Column(name = "ship_street")),
    @AttributeOverride(name = "city",   column = @Column(name = "ship_city")),
    @AttributeOverride(name = "zip",    column = @Column(name = "ship_zip")) })
private Address shipping;                          // ship_street, ship_city, ship_zip
```

**Why `CHAR(3)` needs `@JdbcTypeCode(SqlTypes.CHAR)`:** a Java `String` maps to `VARCHAR` by default.
The migration says `CHAR(3)`, and Hibernate's validator treats `CHAR` and `VARCHAR` as different types.
Without the annotation the app stops with
`found [character (Types#CHAR)], but expecting [varchar(3) (Types#VARCHAR)]`.
Records are used so `Money` is immutable; `times()` returns a new value instead of changing the old one.

### 3.2 Enums stored as text

```java
@Enumerated(EnumType.STRING)
@Column(nullable = false, length = 20)
private Category category;
```

`EnumType.STRING` stores `'HARDWARE'`. The default `ORDINAL` would store `1`, which silently changes
meaning if someone reorders or inserts an enum constant. The test proves it with a native query.

### 3.3 Ids from sequences, `allocationSize = 50`

```java
@Id
@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "product_seq")
@SequenceGenerator(name = "product_seq", sequenceName = "product_seq", allocationSize = 50)
private Long id;
```

```sql
CREATE SEQUENCE product_seq START WITH 1 INCREMENT BY 50;
```

Hibernate asks the database for one sequence value and then hands out the next **50 ids from memory**
before asking again, which saves round-trips when inserting many rows. This only works if the two
numbers agree:

* `allocationSize` (Java) **must equal** `INCREMENT BY` (SQL).
* If they differ, Hibernate stops at start-up with
  `The increment size of the [product_seq] sequence is set to [50] ... but the mapped database sequence increment size is [1]`.

There is **no `setId`**: the database/Hibernate owns the id, application code must not assign it.
Each entity has its own sequence (`customer_seq`, `product_seq`, `orders_seq`, `order_line_seq`).

### 3.4 Associations: `@ManyToOne(fetch = LAZY)`

```java
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "customer_id", nullable = false)
private Customer customer;
```

* The many side owns the foreign key (`orders.customer_id`).
* `LAZY` means Hibernate loads a **proxy** and only runs the SELECT for the customer when you first use
  it. The default for `@ManyToOne` is `EAGER`, which is a common source of the N+1 problem later in the lab.
* `optional = false` plus `nullable = false` match the `NOT NULL` constraint in the SQL.

### 3.5 Optimistic locking: `@Version`

`Order.version` is incremented by Hibernate on every update. An update is sent as
`UPDATE ... SET ..., version=? WHERE id=? AND version=?`. If someone else changed the row first,
zero rows match and Hibernate throws an `OptimisticLockException` instead of silently overwriting.
(Task 1 only maps the field; later tasks exercise it.)

### 3.6 Protected no-arg constructors

JPA creates entities by reflection, so it needs a no-arg constructor. Making it `protected` lets
Hibernate (and lazy proxies) use it while stopping application code from creating half-empty objects.
Real construction goes through the public constructor, e.g. `new Product(sku, name, price, category, stock)`.

### 3.7 Naming rules at work

Spring Boot's naming strategy turns `placedAt` into `placed_at`, and `Order` is mapped explicitly with
`@Table(name = "orders")` because `order` is an SQL keyword. Embeddable columns use the explicit
`@Column(name = ...)` values so they match the SQL exactly.

---

## 4. The migration at a glance

`V1__init.sql` creates **5 sequences** and **6 tables**:

| Table | Notes |
|---|---|
| `product` | `sku` UNIQUE, `amount`/`currency` from `Money`, `stock` CHECK (>= 0) |
| `customer` | `email` UNIQUE, billing `street/city/zip`, shipping `ship_*` |
| `orders` | FK to `customer`, `placed_at TIMESTAMP WITH TIME ZONE`, `version` default 0 |
| `order_line` | FKs to `orders` and `product`, `quantity` CHECK (> 0), `amount`/`currency` |
| `tag`, `product_tag` | created now, mapped in a later task (no entity yet) |

Flyway records the run in `flyway_schema_history` with a checksum, so **never edit an applied
migration** in a shared database; add `V2__...sql` instead. (Here the DB is in-memory and rebuilt on
every start, so editing is safe for the experiments.)

---

## 5. The test

`ProductMappingTest` uses two annotations that matter:

* `@DataJpaTest` boots only the JPA slice (entities, repositories, Flyway), not the web layer.
* `@AutoConfigureTestDatabase(replace = Replace.NONE)` keeps the **datasource configured in
  `application.yml`** (H2 `jdbc:h2:mem:shop`). By default `@DataJpaTest` replaces it with its own
  embedded database. Keeping the real one means the test exercises exactly the setup the application uses:
  Flyway applies `V1__init.sql` and Hibernate validates the entities against it.

The test body:

1. `persistAndFlush` — INSERT is really sent to the database (`select next value for product_seq` then `insert`).
2. `clear()` — empties the persistence context (first-level cache).
3. `find(...)` — must now run a real SELECT, so the data comes from the database, not from memory.
4. Assert `Money` and `Category` round-trip, and a **native query** reads the raw `category` column to prove it contains the text `HARDWARE`.

`flush()` + `clear()` before reloading is the key habit: otherwise `find` returns the same Java object
from cache and the test passes even if the mapping is broken.

---

## 6. How to run

Requirements: JDK 25 and the bundled Maven wrapper (no separate Maven install needed).

```bash
cd SE-LAB-Group3

# run the application (Flyway migrates, Hibernate validates, app starts on :8080)
./mvnw spring-boot:run

# run only the Task 1 test
./mvnw -q test -Dtest=ProductMappingTest

# run everything (ProductMappingTest + contextLoads)
./mvnw test
```

**Expected application output** (trimmed):

```text
DbMigrate : Migrating schema "PUBLIC" to version "1 - init"
DbMigrate : Successfully applied 1 migration to schema "PUBLIC", now at version v1
LocalContainerEntityManagerFactoryBean : Initialized JPA EntityManagerFactory for persistence unit 'default'
ShopJpaApplication : Started ShopJpaApplication in ~2 seconds
```

**Expected test output:** `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`.

**Inspect the database** while the app runs: open <http://localhost:8080/h2-console> with
JDBC URL `jdbc:h2:mem:shop`, user `sa`, empty password. You should see the 6 tables,
`FLYWAY_SCHEMA_HISTORY`, and the 5 sequences.

---

## 7. Experiment: break it on purpose

The first two experiments edit **only the SQL**, leaving the Java unchanged (the third edits only the
Java). Run `./mvnw spring-boot:run` and revert afterwards. The first two were run for this lab; the third
is the error the first start-up produced before the annotation was added.

| Change in `V1__init.sql` | Error you get |
|---|---|
| rename `sku` to `code` in `product` | `Schema validation: missing column [sku] in table [product]` |
| `product_seq ... INCREMENT BY 1` | `The increment size of the [product_seq] sequence is set to [50] ... increment size is [1]` |
| remove `@JdbcTypeCode(SqlTypes.CHAR)` from `Money.currency` (SQL unchanged) | `wrong column type encountered in column [currency] ... found [character (Types#CHAR)], but expecting [varchar(3) (Types#VARCHAR)]` |

Notice **Flyway succeeds in all cases** (the SQL is valid) and **Hibernate is the one that complains**.
That is the point of splitting the roles.

---

## 8. Acceptance checklist

- [x] All six tables and five sequences are created by `V1__init.sql`; the app starts with `ddl-auto: validate`.
- [x] `Money` and `Address` are `@Embeddable` records; both enums use `EnumType.STRING`; no `setId` anywhere.
- [x] Both deliberate schema mismatches were reproduced and documented in `README.md`.
- [x] `ProductMappingTest` passes against the Flyway schema and proves the enum is stored as text.

## 9. Common problems

| Symptom | Cause / fix |
|---|---|
| `FlywayException: ... db/migration` not found | the folder `src/main/resources/db/migration` must exist with at least one `V*.sql` |
| Test uses a different DB than the app | missing `replace = Replace.NONE`, so Spring swapped in its own embedded database |
| `wrong column type ... CHAR vs VARCHAR` | add `@JdbcTypeCode(SqlTypes.CHAR)` or change the SQL to `VARCHAR` |
| `Table "ORDER" not found` / SQL syntax error | map the entity to `orders` with `@Table`, `order` is a reserved word |
| Edited an applied migration and got a checksum error | with a persistent DB run `flyway repair` or add a new `V2` file; in-memory H2 just restarts clean |
