# How to run the `shop` application

This page explains how to start the application, how to try every task (Task 1 – Task 5 and the challenge), and how
to run the tests. All commands are run from the project folder (the one that contains `pom.xml`).

- [1. What you need](#1-what-you-need)
- [2. Start the application](#2-start-the-application)
- [3. Profiles: which configuration is used](#3-profiles-which-configuration-is-used)
- [4. Try it: one block per task](#4-try-it-one-block-per-task)
- [5. Useful URLs](#5-useful-urls)
- [6. Run the tests](#6-run-the-tests)
- [7. Production profile with PostgreSQL (Docker)](#7-production-profile-with-postgresql-docker)
- [8. Troubleshooting](#8-troubleshooting)

---

## 1. What you need

| Tool | Needed for | Check |
|---|---|---|
| **JDK 25** | everything (`pom.xml` has `<java.version>25</java.version>`) | `java -version` → `25…` |
| Maven | nothing to install: the **Maven wrapper** `./mvnw` (Linux/macOS) or `mvnw.cmd` (Windows) downloads Maven 3.10 itself | `./mvnw -v` |
| Internet | only the first time, to download Maven and the libraries into `~/.m2` | |
| Docker | only for the `prod` profile with PostgreSQL ([section 7](#7-production-profile-with-postgresql-docker)) | `docker --version` |
| `curl` | to call the REST API from a terminal (or use Postman / the browser for `GET`) | `curl --version` |

> Once the libraries are downloaded, you can work offline by adding `-o` to every Maven command, e.g. `./mvnw -o test`.

---

## 2. Start the application

Pick **one** of these ways. All of them start the app on <http://localhost:8080>.

### a) With the Maven wrapper (recommended while developing)

```bash
./mvnw spring-boot:run                 # Linux / macOS
mvnw.cmd spring-boot:run               # Windows (cmd or PowerShell)
```

It compiles the code, then starts it. Stop it with **Ctrl + C**.

### b) As a jar file (like on a server)

```bash
./mvnw package -DskipTests             # builds target/shop-0.0.1-SNAPSHOT.jar
java -jar target/shop-0.0.1-SNAPSHOT.jar
```

### c) From VS Code

Open `src/main/java/edu/itc/shop/ShopApplication.java` and click **Run** above `main`, or use the
**Spring Boot Dashboard** view and press ▶ next to `shop`.

### Change the port

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=8081
java -jar target/shop-0.0.1-SNAPSHOT.jar --server.port=8081
```

### What you see when it starts

```
INFO  edu.itc.shop.ShopApplication   : No active profile set, falling back to 1 default profile: "default"
INFO  e.i.shop.service.EmailNotifier : EmailNotifier ready                        ← Task 2 @PostConstruct
INFO  e.itc.shop.service.SmsNotifier : SmsNotifier ready
INFO  edu.itc.shop.ShopApplication   : Started ShopApplication in 2.776 seconds
INFO  edu.itc.shop.DemoRunner        : 401 beans; notifiers = [emailNotifier, smsNotifier, …]   ← Task 2 demo
INFO  e.i.shop.service.EmailNotifier : EMAIL to=ann text=Welcome ann! It is 18:59:16
INFO  e.itc.shop.service.SmsNotifier : SMS to=bob text=Welcome bob! It is 18:59:16
INFO  edu.itc.shop.DemoRunner        : prototype instances differ: true
```

The database is **H2 in memory**: it is created from `schema.sql` + `data.sql` at every start and **lost when the
app stops**. Every restart begins with the same 5 products and 3 customers.

---

## 3. Profiles: which configuration is used

A profile selects extra configuration and beans. Choose it with `-Dspring-boot.run.profiles=…` (Maven) or
`--spring.profiles.active=…` (jar).

| Profile | How to start | Database | Notifier used by `WelcomeService` | Config files read |
|---|---|---|---|---|
| *(none)* = default | `./mvnw spring-boot:run` | H2 in memory `jdbc:h2:mem:shop` | `EmailNotifier` | `application.properties`, `application.yml` |
| `dev` | `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` | same H2 | `ConsoleNotifier` (prints `CONSOLE to=…`) | same as default ¹ |
| `prod` | see [section 7](#7-production-profile-with-postgresql-docker) | **PostgreSQL** (`DB_URL`, `DB_USER`, `DB_PASSWORD`) | `EmailNotifier` | + `application-prod.yml` |
| `test` | only used by the tests (`@ActiveProfiles("test")`) | a new H2 database per test context + `data-test.sql` | `EmailNotifier` | + `src/test/resources/application-test.yml` |

¹ `application_Dev.yml` (with an underscore and a capital D) is **not** read for the `dev` profile. Spring Boot only
looks for `application-dev.yml`. See [task5.md](task5.md#known-problems).

---

## 4. Try it: one block per task

Start the app (default profile), open a second terminal, and copy the commands.
On Windows PowerShell write `curl.exe` instead of `curl`.

### Task 1: first REST controller

```bash
curl localhost:8080/hello
# {"message":"Hello, world!!!","at":"2026-10-29T11:58:39.928841194Z"}
curl "localhost:8080/hello?name=Sokha"
# {"message":"Hello, Sokha!","at":"2026-10-29T11:58:39.937326059Z"}
```

### Task 2: IoC / DI

Nothing to call. Look at the **start-up log** ([section 2](#what-you-see-when-it-starts)): which notifier is used,
the `@PostConstruct` messages and the prototype scope check. Start with `-Dspring-boot.run.profiles=dev` and the
first message becomes `CONSOLE to=ann …`.

### Task 3: database with `JdbcClient`

Open the H2 console: <http://localhost:8080/h2-console>

| Field | Value |
|---|---|
| JDBC URL | `jdbc:h2:mem:shop` |
| User Name | `sa` |
| Password | *(empty)* |

Then run `SELECT * FROM product;`.

### Task 4: product REST API

```bash
curl localhost:8080/api/products                       # all products
curl "localhost:8080/api/products?q=mo"                # search by name: Mouse, Monitor
curl localhost:8080/api/products/1                     # one product
curl -i localhost:8080/api/products/99                 # 404 problem detail

curl -i -X POST localhost:8080/api/products -H 'Content-Type: application/json' \
     -d '{"name":"USB-C cable","price":7.90,"stock":25}'          # 201 + Location: /api/products/6
curl -X POST localhost:8080/api/products -H 'Content-Type: application/json' \
     -d '{"name":"","price":0,"stock":-1}'                          # 400 with 3 field errors

curl -X PATCH localhost:8080/api/products/1/stock -H 'Content-Type: application/json' -d '{"delta":-3}'    # stock 10 → 7
curl -X PATCH localhost:8080/api/products/1/stock -H 'Content-Type: application/json' -d '{"delta":-100}'  # 409
curl -i -X DELETE localhost:8080/api/products/6                     # 204
```

### Task 5: configuration, Actuator, logging

```bash
curl localhost:8080/actuator/health          # includes "lowStock":{"status":"UP"} (custom health indicator)
curl localhost:8080/actuator/info
curl localhost:8080/actuator/metrics/http.server.requests
curl -i localhost:8080/hello | grep X-Request-Id      # request id added by RequestIdFilter

# switch the TimingAspect on at runtime (DEBUG level), then call a service
curl -X POST localhost:8080/actuator/loggers/edu.itc.shop -H 'Content-Type: application/json' \
     -d '{"configuredLevel":"DEBUG"}'
curl localhost:8080/api/products/1
# log: DEBUG [df62c113] e.i.shop.service.TimingAspect : ProductService.findById(..) took 5033 µs

# the validated settings stop a wrong start-up:
java -jar target/shop-0.0.1-SNAPSHOT.jar --shop.tax-rate=2.0
# APPLICATION FAILED TO START … Property: shop.taxRate  Value: "2.0"  Reason: must be less than or equal to 1.0
```

The JSP pages `/products` and `/products/new` currently answer **500**. The reason and the fix are in
[task5.md](task5.md#known-problems).

### Challenge: customers and orders

```bash
curl localhost:8080/api/customers

# place an order: 201, stock goes down
curl -i -X POST localhost:8080/api/orders -H 'Content-Type: application/json' -H 'X-User: sokha' \
     -d '{"customerId":1,"lines":[{"productId":3,"qty":2},{"productId":2,"qty":1}]}'
curl localhost:8080/api/products/3                     # Keyboard: 30 → 28

# one line has too little stock: 409 and NOTHING changes
curl -i -X POST localhost:8080/api/orders -H 'Content-Type: application/json' \
     -d '{"customerId":1,"lines":[{"productId":3,"qty":2},{"productId":2,"qty":999}]}'
curl localhost:8080/api/products/3                     # still 28

# list with filter and paging, read one order
curl "localhost:8080/api/orders?customerId=1&status=NEW&page=0&size=5"
curl localhost:8080/api/orders/1

# cancel: stock comes back, e-mail notification in the log; a second cancel is 409
curl -X POST localhost:8080/api/orders/1/cancel
curl localhost:8080/api/products/3                     # back to 30
curl -X POST localhost:8080/api/orders/1/cancel        # 409
```

Look at the application log: every `place` / `cancel` writes an `AUDIT who=… what=… when=… outcome=…` line with the
request id. Details are in [challenge.md](challenge.md).

---

## 5. Useful URLs

| URL | What | Task |
|---|---|---|
| <http://localhost:8080/hello> | greeting JSON | 1 |
| <http://localhost:8080/h2-console> | database console (`jdbc:h2:mem:shop`, user `sa`) | 3 |
| <http://localhost:8080/api/products> | product REST API | 4 |
| <http://localhost:8080/actuator> | list of Actuator endpoints (`health`, `info`, `metrics`, `loggers`) | 5 |
| <http://localhost:8080/actuator/health> | health, including the low-stock check | 5 |
| <http://localhost:8080/products> | JSP product page (currently 500, see task5.md) | 5 |
| <http://localhost:8080/api/customers> | customers | challenge |
| <http://localhost:8080/api/orders> | orders (paged) | challenge |

---

## 6. Run the tests

```bash
./mvnw test                  # all 59 tests
./mvnw verify                # tests + build the jar   → "Tests run: 59, Failures: 0" and "BUILD SUCCESS"
./mvnw clean verify          # from scratch (use after renaming or moving classes)
```

| Only… | Command |
|---|---|
| one test class | `./mvnw test -Dtest=ProductControllerTest` |
| one test method | `./mvnw test -Dtest='OrderFlowE2ETest#lineWithoutEnoughStockIs409AndNothingChanges'` |
| all repository tests (`@JdbcTest`) | `./mvnw test -Dtest='*RepositoryTest'` |
| all controller tests (`@WebMvcTest`) | `./mvnw test -Dtest='*ControllerTest'` |
| the challenge | `./mvnw test -Dtest='edu.itc.shop.order.*Test'` |

Test reports are written to `target/surefire-reports/`. The tests need **no** database and **no** Docker.

| Test class | Task | Kind |
|---|---|---|
| `ShopApplicationTests` | 1 | `@SpringBootTest`: the whole context starts |
| `WelcomeServiceTest` | 2 | plain unit test with a fake notifier and a fixed clock |
| `Test/RepositoryTest.java` (class `ProductRepositoryTest`) | 3 | `@JdbcTest` |
| `Test/ProductServiceTest` | 3 | `@SpringBootTest`: `restock` rolls back |
| `web/ProductControllerTest` | 4 | `@WebMvcTest` + `@MockitoBean` |
| `order/*`, `customer/*`, `repository/ProductStockRepositoryTest` | challenge | unit, `@JdbcTest`, `@WebMvcTest`, end-to-end |

---

## 7. Production profile with PostgreSQL (Docker)

```bash
export DB_PASSWORD=$(openssl rand -hex 16)       # choose a password; never commit it
docker compose up -d                              # starts PostgreSQL 17 from docker-compose.yml on localhost:5432
docker compose ps                                 # wait until it says (healthy)

DB_URL=jdbc:postgresql://localhost:5432/shop DB_USER=shop \
  ./mvnw spring-boot:run -Dspring-boot.run.profiles=prod

docker compose down                               # stop PostgreSQL (add -v to delete its data too)
```

Windows PowerShell:

```powershell
$env:DB_PASSWORD = "choose-a-password"
docker compose up -d
$env:DB_URL = "jdbc:postgresql://localhost:5432/shop"; $env:DB_USER = "shop"
mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=prod"
```

- In `prod`, the tables are created by `src/main/resources/db/postgresql/schema.sql` and filled by
  `db/postgresql/data.sql`. Both are safe to run at every start (`IF NOT EXISTS`, `ON CONFLICT DO NOTHING`).
- Unlike H2, the data **stays** after a restart (it lives in the Docker volume `shop-data`).
- The H2 console is switched off in `prod`.

---

## 8. Troubleshooting

| Problem | Cause and fix |
|---|---|
| `Web server failed to start. Port 8080 was already in use.` | Another app (or an old run) uses the port. Stop it (`ss -ltnp \| grep 8080` or `lsof -i :8080` shows the PID), or start on another port: `--server.port=8081`. |
| `release version 25 not supported` / `invalid target release: 25` | Your `java` is older than 25. Install JDK 25 and check `java -version` (and `JAVA_HOME`). |
| `class LowStockHealthIndicator is public, should be declared in a file named LowStockHealthIndicator.java` | An old checkout. The file was renamed from `LOWStockHealthIndicator.java`; pull the latest `jingpo` branch. On Windows/macOS, Git may not notice a rename that only changes upper/lower case: run `git mv` with the exact names. |
| `./mvnw: Permission denied` | `chmod +x mvnw` |
| My products / orders are gone after a restart | Normal: H2 is in memory. Use the `prod` profile with PostgreSQL to keep data. |
| `prod` does not start: `Driver org.postgresql.Driver claims to not accept jdbcUrl, ${DB_URL}` | `DB_URL` is not set, so the text `${DB_URL}` is used as the URL. Set `DB_URL`, `DB_USER` and `DB_PASSWORD` first ([section 7](#7-production-profile-with-postgresql-docker)). |
| `/products` or `/products/new` gives 500 | The JSP files are missing and Thymeleaf takes over: see [task5.md](task5.md#known-problems). |
| An unknown URL (e.g. `/abc`) gives 500, not 404 | `ApiExceptionHandler` (Task 4) catches every exception, also "no resource found": see [task4.md](task4.md#known-problems). |
| Tests fail after renaming or moving a class | Old `.class` files are still in `target/`. Run `./mvnw clean verify`. |
