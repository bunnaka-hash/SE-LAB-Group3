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
