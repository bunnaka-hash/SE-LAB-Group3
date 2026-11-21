# Task 2 – HTTP transcript

```
$ curl -si -X POST localhost:8080/api/products -H 'Content-Type: application/json' -d '{"name":"Teapot","sku":"TP-01","price":31.00,"stock":5}'
HTTP/1.1 201 Created
Location: http://localhost:8080/api/products/4
Content-Type: application/json
Content-Length: 62

{"id":4,"name":"Teapot","price":31.00,"sku":"TP-01","stock":5}

$ curl -si -X POST localhost:8080/api/products -H 'Content-Type: application/json' -d '{"name":"Teapot","sku":"TP-01","price":31.00,"stock":5}'
HTTP/1.1 409 Conflict
Content-Length: 0



$ curl -si -X POST localhost:8080/api/products -H 'Content-Type: application/xml' -H 'Accept: application/xml' -d '<product><name>Bowl</name><sku>BW-01</sku><price>9.90</price><stock>3</stock></product>'
HTTP/1.1 201 Created
Location: http://localhost:8080/api/products/5
Content-Type: application/xml
Content-Length: 149

<?xml version="1.0" encoding="UTF-8" standalone="yes"?><product id="5"><name>Bowl</name><sku>BW-01</sku><price>9.90</price><stock>3</stock></product>

$ curl -s localhost:8080/api/products/4 -H 'Accept: application/xml'
<?xml version="1.0" encoding="UTF-8" standalone="yes"?><product id="4"><name>Teapot</name><sku>TP-01</sku><price>31.00</price><stock>5</stock></product>

$ curl -si -X PATCH localhost:8080/api/products/1/stock -H 'Content-Type: application/json' -d '{"delta":-2}'
HTTP/1.1 200 OK
Content-Type: application/json
Content-Length: 63

{"id":1,"name":"Kettle","price":24.90,"sku":"KT-01","stock":10}

$ curl -si -X PATCH localhost:8080/api/products/3/stock -H 'Content-Type: application/json' -d '{"delta":-1}'
HTTP/1.1 409 Conflict
Content-Length: 0



$ curl -si -X DELETE localhost:8080/api/products/4
HTTP/1.1 204 No Content



$ curl -si -X DELETE localhost:8080/api/products/4
HTTP/1.1 404 Not Found
Content-Length: 0



$ curl -si -X POST localhost:8080/api/products -H 'Content-Type: text/plain' -d 'Teapot'
HTTP/1.1 415 Unsupported Media Type
Content-Length: 0



```
