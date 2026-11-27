# Lab 05 – security session

## Task 1a – default security (no SecurityConfig yet)

Started with `--spring.security.user.name=user` (see README for why).

```
$ curl -i localhost:8080/api/products
HTTP/1.1 401 
Set-Cookie: JSESSIONID=F2B11B5DBE77C5D82557212557F1869E; Path=/; HttpOnly
WWW-Authenticate: Basic realm="Realm", charset="UTF-8"
X-Content-Type-Options: nosniff
X-XSS-Protection: 0
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Pragma: no-cache
Expires: 0
X-Frame-Options: DENY
Content-Length: 0
Date: Tue, 24 Nov 2026 15:29:03 GMT



$ curl -i -u user:<generated password> localhost:8080/api/products
HTTP/1.1 200 
X-Content-Type-Options: nosniff
X-XSS-Protection: 0
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Pragma: no-cache
Expires: 0
X-Frame-Options: DENY
Content-Type: application/json
Content-Length: 158
Date: Tue, 24 Nov 2026 15:29:03 GMT

[{"id":1,"name":"USB-C cable","price":7.90,"stock":25},{"id":2,"name":"Keyboard","price":49.00,"stock":10},{"id":3,"name":"Monitor","price":189.00,"stock":4}]

```

## Task 1b – explicit SecurityFilterChain

```
$ curl -i localhost:8080/api/products
HTTP/1.1 200 
X-Content-Type-Options: nosniff
X-XSS-Protection: 0
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Pragma: no-cache
Expires: 0
X-Frame-Options: DENY
Content-Type: application/json
Content-Length: 158
Date: Tue, 24 Nov 2026 15:29:16 GMT

[{"id":1,"name":"USB-C cable","price":7.90,"stock":25},{"id":2,"name":"Keyboard","price":49.00,"stock":10},{"id":3,"name":"Monitor","price":189.00,"stock":4}]

$ curl -i -X POST localhost:8080/api/products -H 'Content-Type: application/json' -d '{"name":"Mouse","price":19.9,"stock":5}'
HTTP/1.1 401 
WWW-Authenticate: Basic realm="Realm"
X-Content-Type-Options: nosniff
X-XSS-Protection: 0
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Pragma: no-cache
Expires: 0
X-Frame-Options: DENY
Content-Type: application/problem+json
Transfer-Encoding: chunked
Date: Tue, 24 Nov 2026 15:29:16 GMT

{"detail":"Authentication required","instance":"/api/products","status":401,"title":"Unauthorized","type":"about:blank"}

$ curl -i -u alice:alice -X DELETE localhost:8080/api/products/1
HTTP/1.1 403 
X-Content-Type-Options: nosniff
X-XSS-Protection: 0
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Pragma: no-cache
Expires: 0
X-Frame-Options: DENY
Content-Type: application/problem+json
Transfer-Encoding: chunked
Date: Tue, 24 Nov 2026 15:29:16 GMT

{"detail":"Access denied","instance":"/api/products/1","status":403,"title":"Forbidden","type":"about:blank"}

$ curl -i -u admin:admin -X DELETE localhost:8080/api/products/1
HTTP/1.1 204 
X-Content-Type-Options: nosniff
X-XSS-Protection: 0
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Pragma: no-cache
Expires: 0
X-Frame-Options: DENY
Date: Tue, 24 Nov 2026 15:29:16 GMT



```

## Task 3: Roles and Method Security

### Alice creates an order
HTTP 201 Created

### Bob attempts to read Alice's order
HTTP 403 Forbidden

### Admin reads Alice's order
HTTP 200 OK

### Security tests
OrderServiceSecurityTest: 5 tests
OrderControllerSecurityTest: 3 tests

## Task 5 – Hardening (CSRF, CORS, headers, XSS, secrets, logging)

Environment for all transcripts: `JWT_SECRET`, `SHOP_SEED_ADMIN_PASSWORD`, `SHOP_SEED_CUSTOMER_PASSWORD` set
(see `.env.example`). CSRF decision: [`docs/csrf.md`](docs/csrf.md).

### CORS – one origin only

```
$ curl -i -X OPTIONS localhost:8080/api/orders -H 'Origin: http://localhost:5173' \
       -H 'Access-Control-Request-Method: POST' -H 'Access-Control-Request-Headers: authorization,content-type'
HTTP/1.1 200
Vary: Origin
Vary: Access-Control-Request-Method
Vary: Access-Control-Request-Headers
Access-Control-Allow-Origin: http://localhost:5173
Access-Control-Allow-Methods: GET,POST,PUT,DELETE
Access-Control-Allow-Headers: authorization, content-type
Access-Control-Expose-Headers: Location
Access-Control-Max-Age: 3600

$ curl -i -X OPTIONS localhost:8080/api/orders -H 'Origin: http://evil.example' \
       -H 'Access-Control-Request-Method: POST' -H 'Access-Control-Request-Headers: authorization,content-type'
HTTP/1.1 403
Vary: Origin
Vary: Access-Control-Request-Method
Vary: Access-Control-Request-Headers
Invalid CORS request            <- no Access-Control-Allow-Origin header at all
```

### Security headers

```
$ curl -I localhost:8080/api/products | grep -iE 'x-frame|strict-transport|content-security|referrer|x-content'
X-Content-Type-Options: nosniff
Strict-Transport-Security: max-age=31536000 ; includeSubDomains
X-Frame-Options: DENY
Content-Security-Policy: default-src 'self'; frame-ancestors 'none'; object-src 'none'
Referrer-Policy: strict-origin-when-cross-origin
```

HSTS is written over plain `http://localhost` only because `shop.security.hsts-over-http=true` is set for the
lab; production keeps the default (HTTPS requests only).

### Stored XSS – defeated

```
$ curl -si -X POST localhost:8080/api/reviews -H "Authorization: Bearer $ALICE" -H 'Content-Type: application/json' \
       -d '{"productId":2,"stars":1,"text":"<img src=x onerror=\"document.title='"'"'pwned'"'"'\">"}'
HTTP/1.1 201
Location: /api/products/2/reviews

{"id":1,"author":"alice","stars":1,"text":"<img src=x onerror=\"document.title='pwned'\">"}

$ curl -si -X POST localhost:8080/api/reviews -H "Authorization: Bearer $ALICE" -H 'Content-Type: application/json' \
       -d '{"productId":2,"stars":9,"text":""}'
{"detail":"Validation failed","status":400,"errors":[{"field":"stars","message":"must be less than or equal to 5"},{"field":"text","message":"must not be blank"}]}

$ curl -si -X POST localhost:8080/api/reviews -H 'Content-Type: application/json' -d '{"productId":2,"stars":3,"text":"x"}'
HTTP/1.1 401
```

(a) JSON API – the payload is stored unchanged and Jackson escapes the quotes; `application/json` + `nosniff`:

```
$ curl -si localhost:8080/api/products/2/reviews | grep -iE 'content-type|x-content-type|^\['
X-Content-Type-Options: nosniff
Content-Type: application/json
[{"id":1,"author":"alice","stars":1,"text":"<img src=x onerror=\"document.title='pwned'\">"}]
```

(b) Thymeleaf page with `th:text` – shown as literal text, no `<img>` element exists:

```
$ curl -s localhost:8080/products/2/reviews.html | grep '<p'
    <p>&lt;img src=x onerror=&quot;document.title=&#39;pwned&#39;&quot;&gt;</p>
```

The page also carries `Content-Security-Policy: default-src 'self'`, which blocks inline event handlers even
if escaping were ever bypassed (defence in depth).

`th:utext` counter-example: *not yet recorded* (needs the unmerged demo branch and a browser screenshot).

### Secrets – fail fast

```
$ git grep -n -i -E "password|secret" -- '*.yml' '*.properties' '*.java'   (relevant hits)
src/main/resources/application.properties:3:shop.seed.admin-password=${SHOP_SEED_ADMIN_PASSWORD}
src/main/resources/application.properties:4:shop.seed.customer-password=${SHOP_SEED_CUSTOMER_PASSWORD}
src/main/resources/application.yml:22:    jwt-secret: ${JWT_SECRET}
src/test/java/edu/itc/shop/AuthFlowIT.java:28:  @TestPropertySource(properties = "JWT_SECRET=0123456789abcdef...")   <- test-only value
(all other hits are identifiers such as passwordHash / PasswordEncoder, not values)

$ JWT_SECRET= ./mvnw -q spring-boot:run
***************************
APPLICATION FAILED TO START
***************************

Description:

Binding to target edu.itc.shop.config.SecurityProps failed:

    Property: shop.security.jwtSecret
    Value: ""
    Origin: class path resource [application.yml] - 22:17
    Reason: size must be between 32 and 2147483647

    Property: shop.security.jwtSecret
    Value: ""
    Origin: class path resource [application.yml] - 22:17
    Reason: must not be blank
```

### Security event log (logger `SECURITY`, request id in `rid=`)

```
20:46:30.291 WARN  rid=d110ec28 SECURITY : login.failed user=alice reason=BadCredentialsException
20:46:30.367 INFO  rid=01f667eb SECURITY : login.ok user=alice type=UsernamePasswordAuthenticationToken
20:46:30.446 WARN  rid=1456e3ee SECURITY : access.denied user=alice object=OrderService.ship(1)
20:46:30.455 WARN  rid=9e2ac75e SECURITY : login.failed user=<bearer-token> reason=InvalidBearerTokenException
```

The same id is returned to the client in the `X-Request-Id` response header. A bearer token is never logged.
