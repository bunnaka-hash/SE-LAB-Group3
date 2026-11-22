# Task 4 - Providers and filters

## 1. POST without key (401 problem+json)
```
curl -i -X POST localhost:8080/api/products -H "Content-Type: application/json" -d "{}"
<paste output>
```

## 2. POST with wrong key (401)
```
curl -i -X POST localhost:8080/api/products -H "Content-Type: application/json" -H "X-Api-Key: wrong" -d "{}"
<paste output>
```

## 3. POST with right key (201)
```
curl -i -X POST localhost:8080/api/products -H "Content-Type: application/json" -H "X-Api-Key: demo-123" -d "<valid product JSON>"
<paste output>
```

## 4. OPTIONS preflight
```
curl -i -X OPTIONS localhost:8080/api/products -H "Origin: http://localhost:5173" -H "Access-Control-Request-Method: POST"
<paste output>
```

## 5. X-Correlation-Id echoed back
```
curl -si localhost:8080/api/products/1 -H "X-Correlation-Id: order-check-42"
<paste output>
```

## 6. Gzip with Accept-Encoding
```
curl -sv --compressed localhost:8080/api/products -o NUL
<paste the Content-Encoding and Vary lines>
```

## 7. No compression without Accept-Encoding
```
curl -sv localhost:8080/api/products -o NUL
<paste output showing no Content-Encoding>
```