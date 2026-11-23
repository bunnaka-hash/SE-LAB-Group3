# Task 3: Parameters and validation

## 1. Filtered and sorted list

```
$ curl -i "localhost:8080/api/products?minPrice=5%20EUR&inStock=true&sort=price,desc"
HTTP/1.1 200 OK
Content-Type: application/json
Content-Length: 125

[{"id":1,"name":"Kettle","price":24.90,"sku":"KT-01","stock":12},{"id":2,"name":"Mug","price":6.50,"sku":"MG-01","stock":40}]
```

## 2. minPrice=abc (400 from the converter)

```
$ curl -i "localhost:8080/api/products?minPrice=abc"
HTTP/1.1 400 Bad Request
Content-Type: application/problem+json
Connection: close
Content-Length: 125

{"detail":"Cannot parse Money from 'abc'","instance":"/api/products","status":400,"title":"Bad Request","type":"about:blank"}
```

## 3. sort=colour,asc (400)

```
$ curl -i "localhost:8080/api/products?sort=colour,asc"
HTTP/1.1 400 Bad Request
Content-Type: application/problem+json
Connection: close
Content-Length: 148

{"detail":"Cannot sort by 'colour'; allowed: name, price, stock","instance":"/api/products","status":400,"title":"Bad Request","type":"about:blank"}
```

## 4. Invalid POST (400, one error per field)

```
$ curl -i -X POST localhost:8080/api/products -H 'Content-Type: application/json' -d '{"name":"","sku":"kettle","price":0,"stock":-1}'
```

## 5. Unknown product (404 as problem+json)

```
$ curl -i localhost:8080/api/products/99
HTTP/1.1 404 Not Found
Content-Type: application/problem+json
Content-Length: 117
## Unknown sort field

```console
$ curl -i 'http://localhost:8080/api/products?sort=colour,asc'
HTTP/1.1 400 Bad Request
Content-Type: application/problem+json

{"detail":"Validation failed","errors":[{"field":"sort","message":"use field,asc or field,desc with field one of name, price, stock"}],"instance":"/api/products","status":400,"title":"Invalid request","type":"about:blank"}
```

## Invalid POST body

```console
$ curl -i -X POST 'http://localhost:8080/api/products' \
    -H 'Content-Type: application/json' \
    --data-binary '{"name":"","sku":"kettle","price":0,"stock":-1}'
HTTP/1.1 400 Bad Request
Content-Type: application/problem+json

{"detail":"Validation failed","errors":[{"field":"name","message":"must not be blank"},{"field":"sku","message":"must match \"[A-Z]{2}-\\d{2}\""},{"field":"stock","message":"must be greater than or equal to 0"},{"field":"price","message":"must be greater than or equal to 0.01"}],"instance":"/api/products","status":400,"title":"Invalid request","type":"about:blank"}
```

## Not found

```console
$ curl -i 'http://localhost:8080/api/products/99'
HTTP/1.1 404 Not Found
Content-Type: application/problem+json

{"detail":"Product 99 not found","instance":"/api/products/99","status":404,"title":"Not Found","type":"about:blank"}
```

## 6. 500 experiment (no stack trace in the body)

```
$ curl -i localhost:8080/api/products/1
HTTP/1.1 200 OK
Content-Type: application/json
Content-Length: 63

{"id":1,"name":"Kettle","price":24.90,"sku":"KT-01","stock":12}
## Temporary 500 experiment

An `IllegalStateException` was temporarily thrown from `GET /products`. The response contained a reference ID and no exception or stack trace:

```console
$ curl -i 'http://localhost:8080/api/products'
HTTP/1.1 500 Internal Server Error
Content-Type: application/problem+json

{"detail":"reference 582980c8-ebc3-409a-9697-9b63152d6c7c","instance":"/api/products","status":500,"title":"Internal Server Error","type":"about:blank"}
```
