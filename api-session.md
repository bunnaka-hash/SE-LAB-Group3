# Lab-03 · Task 3 · API session

Start the app with demo data:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Replace each `<paste …>` block with the real output from your machine (response + the SQL lines from the console).

## 1. Paged order summaries (interface projection)

```bash
curl -s "localhost:8080/api/orders?status=PAID&page=0&size=2&sort=placedAt,desc" | jq
```

```json
<paste response>
```

SQL log (should be 2 statements, only id, status, placed_at, customer name):

```
<paste the select … and select count(…) lines>
```

## 2. Product search (Specifications + record projection)

```bash
curl -s "localhost:8080/api/products?q=key&category=HARDWARE&maxPrice=60&inStock=true" | jq
curl -s "localhost:8080/api/products?page=1&size=5&sort=price.amount,desc" | jq
```

```json
<paste response>
```

SQL log:

```
<paste>
```

## 3. Customer orders with lines (fetch join, readOnly transaction)

```bash
curl -s "localhost:8080/api/customers/ada@example.com/orders" | jq '.[0]'
curl -s -i "localhost:8080/api/customers/nobody@example.com/orders"    # 404 problem detail
```

```json
<paste response>
```

SQL log (one select with join order_line + join product):

```
<paste>
```
