# Task 1 – HTTP transcript

```
$ mvn -q compile exec:java
shop-rest listening on http://localhost:8080/api/

$ curl -i localhost:8080/api/products
HTTP/1.1 200 OK
Content-Type: application/json
Content-Length: 220

[{"id":1,"name":"Kettle","price":24.90,"sku":"KT-01","stock":12,"version":1},{"id":2,"name":"Mug","price":6.50,"sku":"MG-01","stock":40,"version":1},{"id":3,"name":"Tea","price":4.20,"sku":"TE-01","stock":0,"version":1}]

$ curl -i localhost:8080/api/products/2
HTTP/1.1 200 OK
Content-Type: application/json
Content-Length: 71

{"id":2,"name":"Mug","price":6.50,"sku":"MG-01","stock":40,"version":1}

$ curl -i localhost:8080/api/products/99
HTTP/1.1 404 Not Found
Content-Length: 0

$ curl -i localhost:8080/api/products/abc
HTTP/1.1 404 Not Found
Content-Length: 0
```

`/products/99` matches `{id: \d+}`, so it reaches the resource method, which throws the explicit
`NotFoundException`. `/products/abc` matches no template at all, so Jersey itself answers 404
before any resource method runs.
