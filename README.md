# Lab 05 – shop-secure (Spring Security, JWT)

Spring Boot 4.1.3 · Spring Security 7.1 · JDK 25 · Maven 3.9 · H2 (in memory)

> **Version note.** The lab asks for Spring Boot 4.1.1, but start.spring.io no longer offers it,
> so the project was generated with **4.1.3** (same 4.1 line). The Initializr id for
> Spring Web MVC is `web` (the lab's `webmvc` id is rejected); it still adds
> `spring-boot-starter-webmvc`.

## Run

```bash
./mvnw -q verify            # build + tests, exit code 0
./mvnw spring-boot:run      # http://localhost:8080
```

All curl transcripts are in [`security-session.md`](security-session.md).

## Task 1 – default security

With no `SecurityFilterChain` of our own, starting the app (with `--debug`) logged:

```
Using generated security password: 257ee74d-a7af-484b-a43b-67bcd165cdbe

This generated password is for development use only. Your security configuration must be updated before running your application in production.
```

`GET /api/products` then answered **401** without credentials and **200** with
`-u user:<generated password>` (Task 1a in `security-session.md`).

**Which auto-configuration does this** (from the `--debug` condition report):

- **`ServletWebSecurityAutoConfiguration.SecurityFilterChainConfiguration`** – matched because
  there is no `SecurityFilterChain` bean. It registers `defaultSecurityFilterChain`: every request
  must be authenticated, with form login **and** HTTP Basic enabled, CSRF on, and the security
  headers (`X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `Cache-Control: no-store`).
- **`UserDetailsServiceAutoConfiguration`** – matched because there is no `UserDetailsService`,
  `AuthenticationManager`, `AuthenticationProvider` or `JwtDecoder` bean. It creates an
  `InMemoryUserDetailsManager` with one user, `user`, whose password is a random UUID printed at
  startup (the line above).
- **`SecurityFilterAutoConfiguration`** – registers the `springSecurityFilterChain` filter with
  the servlet container, so the chain runs on every request.

**Why we started it with `--spring.security.user.name=user`:** in this project the OAuth2
Resource Server library is on the classpath. The condition report shows that
`UserDetailsServiceAutoConfiguration` then *backs off*
(`@ConditionalOnMissingClass found unwanted class '...OpaqueTokenIntrospector'`), because Boot
assumes a token-based API wants no default user. With no user at all, every call was 401 and
no password was printed. Setting only the user **name** satisfies the condition's
`NameConfigured` branch, so Boot creates the user and still generates and logs the password –
the classic default behaviour.

## Task 1 – explicit `SecurityFilterChain`

`config/SecurityConfig` defines one `SecurityFilterChain` bean (lambda DSL). Rules, first match wins:

| Rule | Access |
|---|---|
| `ERROR` dispatch | permitAll (so `/error` never turns into a 401) |
| `GET /api/products/**` | permitAll |
| `DELETE /api/products/**` | `hasRole("ADMIN")` |
| any other `/api/**` | authenticated |
| anything else | `denyAll()` |

HTTP Basic on, form login off, sessions `STATELESS`, CSRF disabled – safe because this chain
accepts no cookie-based credentials, only `Authorization: Basic` (later `Bearer`) headers, which
a browser never attaches to a cross-site request on its own.

401 and 403 are written as `application/problem+json`: the entry point and the access-denied
handler pass the exception to Spring MVC's `HandlerExceptionResolver`, so `web/ApiExceptionHandler`
produces every error body (401, 403, 404, 400 validation) in one place. The 401 keeps the
`WWW-Authenticate: Basic realm="Realm"` header so `curl -u` and browsers still work.

Temporary users (replaced in Task 2): `admin/admin` (ADMIN), `alice/alice` (CUSTOMER).

## Task 5 – hardening

Configuration comes from the environment only; copy `.env.example` to `.env`, fill it in and export it:

| Variable | Purpose |
|---|---|
| `JWT_SECRET` | HMAC key, at least 32 characters. The app refuses to start if it is missing or blank. |
| `SHOP_SEED_ADMIN_PASSWORD` | password of the seeded `admin` user |
| `SHOP_SEED_CUSTOMER_PASSWORD` | password of the seeded `alice` user |

```bash
set -a; source .env; set +a
./mvnw -q verify
```

- **Filter chains** (new class `HardeningConfig`): `hardenedApi` (`@Order(1)`) handles `/api/**` (Bearer JWT,
  stateless, CORS, headers, request id) and `pages` (`@Order(2)`) handles `/products/**` (public Thymeleaf page).
  The Task 1 `api` chain is unchanged and, having no `@Order`, is evaluated last as the `denyAll` catch-all.
  The only edit in `SecurityConfig` is on the Task 4 `chain` bean (`@Order(10)` + `securityMatcher("/api/**")`):
  two catch-all chains make Spring Security refuse to start (`UnreachableFilterChainException`).
  CSRF reasoning: [`docs/csrf.md`](docs/csrf.md).
- **Task 5 settings** are in their own file `src/main/resources/config/application.properties` (log pattern with
  `rid`, lab-only HSTS flag), so `application.yml` is untouched.
- **CORS**: only `http://localhost:5173` (`CorsConfig`).
- **HSTS note**: browsers/Spring only send HSTS over HTTPS. For the local lab `shop.security.hsts-over-http=true`
  in `config/application.properties` makes it appear on plain `http://localhost`. **Production uses the default** (remove the property).
- **Logging**: logger `SECURITY` (`SecurityEventLogger`) and a request id (`rid`, header `X-Request-Id`).
  `application.yml` still sets Spring Security to TRACE (from Task 1), which is very verbose; filter with
  `grep ' SECURITY '` or lower it with `--logging.level.org.springframework.security=INFO`.
- Evidence (preflight, headers, XSS, fail-fast, log lines): [`security-session.md`](security-session.md).
