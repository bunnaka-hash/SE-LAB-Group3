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
{
  "content": [
    {
      "customer": {
        "name": "Linus Torvalds"
      },
      "id": 30,
      "placedAt": "2026-11-10T18:06:54.225033Z",
      "status": "PAID"
    },
    {
      "customer": {
        "name": "Grace Hopper"
      },
      "id": 26,
      "placedAt": "2026-11-10T18:06:54.217458Z",
      "status": "PAID"
    }
  ],
  "page": {
    "size": 2,
    "number": 0,
    "totalElements": 8,
    "totalPages": 4
  }
}
```

SQL log (should be 2 statements, only id, status, placed_at, customer name):

```
DEBUG org.hibernate.SQL : select o1_0.id,o1_0.status,o1_0.placed_at,c1_0.name from orders o1_0 join customer c1_0 on c1_0.id=o1_0.customer_id where o1_0.status=? order by o1_0.placed_at desc fetch first ? rows only
DEBUG org.hibernate.SQL : select count(o1_0.id) from orders o1_0 where o1_0.status=?
```

## 2. Product search (Specifications + record projection)

```bash
curl -s "localhost:8080/api/products?q=key&category=HARDWARE&maxPrice=60&inStock=true" | jq
curl -s "localhost:8080/api/products?page=1&size=5&sort=price.amount,desc" | jq
```

```json
{
  "content": [
    {
      "id": 1,
      "sku": "KB-1",
      "name": "Office keyboard",
      "amount": 24.9,
      "stock": 40
    },
    {
      "id": 2,
      "sku": "KB-7",
      "name": "Mechanical keyboard",
      "amount": 59,
      "stock": 4
    }
  ],
  "page": {
    "size": 20,
    "number": 0,
    "totalElements": 2,
    "totalPages": 1
  }
}
```

SQL log:

```
DEBUG org.hibernate.SQL : select c1_0.id from customer c1_0 where c1_0.email=? fetch first ? rows only
DEBUG org.hibernate.SQL : select distinct o1_0.id,o1_0.customer_id,l1_0.order_id,l1_0.id,p1_0.id,p1_0.category,p1_0.name,p1_0.amount,p1_0.currency,p1_0.sku,p1_0.stock,l1_0.quantity,l1_0.amount,l1_0.currency,o1_0.placed_at,o1_0.status,o1_0.version from orders o1_0 join customer c1_0 on c1_0.id=o1_0.customer_id join order_line l1_0 on o1_0.id=l1_0.order_id join product p1_0 on p1_0.id=l1_0.product_id where c1_0.email=? order by o1_0.placed_at desc
DEBUG org.hibernate.SQL : select c1_0.id,c1_0.city,c1_0.street,c1_0.zip,c1_0.email,c1_0.name,c1_0.ship_city,c1_0.ship_street,c1_0.ship_zip from customer c1_0 where c1_0.id=?
```

## 3. Customer orders with lines (fetch join, readOnly transaction)

```bash
curl -s "localhost:8080/api/customers/ada@example.com/orders" | jq '.[0]'
curl -s -i "localhost:8080/api/customers/nobody@example.com/orders"    # 404 problem detail
```

```json
[
  {
    "id": 28,
    "status": "CANCELLED",
    "placedAt": "2026-11-10T18:06:54.220994Z",
    "customerName": "Ada Lovelace",
    "lines": [
      {
        "id": 83,
        "sku": "TY-1",
        "productName": "Wooden train set",
        "quantity": 2,
        "unitPrice": 34,
        "lineTotal": 68
      },
      {
        "id": 84,
        "sku": "FD-1",
        "productName": "Organic coffee beans",
        "quantity": 3,
        "unitPrice": 12.9,
        "lineTotal": 38.7
      },
      {
        "id": 85,
        "sku": "TY-2",
        "productName": "Puzzle 1000 pieces",
        "quantity": 2,
        "unitPrice": 14.5,
        "lineTotal": 29
      },
      {
        "id": 86,
        "sku": "FD-2",
        "productName": "Dark chocolate box",
        "quantity": 1,
        "unitPrice": 9.9,
        "lineTotal": 9.9
      }
    ],
    "total": 145.6,
    "currency": "EUR"
  },
  {
    "id": 25,
    "status": "NEW",
    "placedAt": "2026-11-10T18:06:54.215467Z",
    "customerName": "Ada Lovelace",
    "lines": [
      {
        "id": 75,
        "sku": "BK-3",
        "productName": "Domain-Driven Design",
        "quantity": 3,
        "unitPrice": 61.5,
        "lineTotal": 184.5
      },
      {
        "id": 76,
        "sku": "KB-7",
        "productName": "Mechanical keyboard",
        "quantity": 2,
        "unitPrice": 59,
        "lineTotal": 118
      }
    ],
    "total": 302.5,
    "currency": "EUR"
  },
  {
    "id": 22,
    "status": "PAID",
    "placedAt": "2026-11-10T18:06:54.210440Z",
    "customerName": "Ada Lovelace",
    "lines": [
      {
        "id": 65,
        "sku": "MS-2",
        "productName": "Wireless mouse",
        "quantity": 1,
        "unitPrice": 19.9,
        "lineTotal": 19.9
      },
      {
        "id": 66,
        "sku": "FD-2",
        "productName": "Dark chocolate box",
        "quantity": 1,
        "unitPrice": 9.9,
        "lineTotal": 9.9
      },
      {
        "id": 67,
        "sku": "TY-1",
        "productName": "Wooden train set",
        "quantity": 3,
        "unitPrice": 34,
        "lineTotal": 102
      },
      {
        "id": 68,
        "sku": "KB-7",
        "productName": "Mechanical keyboard",
        "quantity": 3,
        "unitPrice": 59,
        "lineTotal": 177
      }
    ],
    "total": 308.8,
    "currency": "EUR"
  },
  {
    "id": 19,
    "status": "SHIPPED",
    "placedAt": "2026-11-10T18:06:54.206236Z",
    "customerName": "Ada Lovelace",
    "lines": [
      {
        "id": 57,
        "sku": "TY-2",
        "productName": "Puzzle 1000 pieces",
        "quantity": 2,
        "unitPrice": 14.5,
        "lineTotal": 29
      },
      {
        "id": 58,
        "sku": "BK-3",
        "productName": "Domain-Driven Design",
        "quantity": 1,
        "unitPrice": 61.5,
        "lineTotal": 61.5
      }
    ],
    "total": 90.5,
    "currency": "EUR"
  },
  {
    "id": 16,
    "status": "CANCELLED",
    "placedAt": "2026-11-10T18:06:54.199673Z",
    "customerName": "Ada Lovelace",
    "lines": [
      {
        "id": 49,
        "sku": "KB-9",
        "productName": "Gaming keyboard",
        "quantity": 1,
        "unitPrice": 129,
        "lineTotal": 129
      },
      {
        "id": 50,
        "sku": "MS-2",
        "productName": "Wireless mouse",
        "quantity": 1,
        "unitPrice": 19.9,
        "lineTotal": 19.9
      }
    ],
    "total": 148.9,
    "currency": "EUR"
  },
  {
    "id": 13,
    "status": "NEW",
    "placedAt": "2026-11-10T18:06:54.194674Z",
    "customerName": "Ada Lovelace",
    "lines": [
      {
        "id": 40,
        "sku": "FD-1",
        "productName": "Organic coffee beans",
        "quantity": 3,
        "unitPrice": 12.9,
        "lineTotal": 38.7
      },
      {
        "id": 41,
        "sku": "MN-4",
        "productName": "27-inch monitor",
        "quantity": 3,
        "unitPrice": 249,
        "lineTotal": 747
      }
    ],
    "total": 785.7,
    "currency": "EUR"
  },
  {
    "id": 10,
    "status": "PAID",
    "placedAt": "2026-11-10T18:06:54.184106Z",
    "customerName": "Ada Lovelace",
    "lines": [
      {
        "id": 29,
        "sku": "BK-1",
        "productName": "Clean Code",
        "quantity": 2,
        "unitPrice": 39.9,
        "lineTotal": 79.8
      },
      {
        "id": 30,
        "sku": "TY-1",
        "productName": "Wooden train set",
        "quantity": 3,
        "unitPrice": 34,
        "lineTotal": 102
      },
      {
        "id": 31,
        "sku": "KB-9",
        "productName": "Gaming keyboard",
        "quantity": 2,
        "unitPrice": 129,
        "lineTotal": 258
      },
      {
        "id": 32,
        "sku": "KB-1",
        "productName": "Office keyboard",
        "quantity": 3,
        "unitPrice": 24.9,
        "lineTotal": 74.7
      }
    ],
    "total": 514.5,
    "currency": "EUR"
  },
  {
    "id": 7,
    "status": "SHIPPED",
    "placedAt": "2026-11-10T18:06:54.178591Z",
    "customerName": "Ada Lovelace",
    "lines": [
      {
        "id": 19,
        "sku": "KB-9",
        "productName": "Gaming keyboard",
        "quantity": 1,
        "unitPrice": 129,
        "lineTotal": 129
      },
      {
        "id": 20,
        "sku": "FD-1",
        "productName": "Organic coffee beans",
        "quantity": 2,
        "unitPrice": 12.9,
        "lineTotal": 25.8
      },
      {
        "id": 21,
        "sku": "BK-2",
        "productName": "Java Persistence",
        "quantity": 3,
        "unitPrice": 54,
        "lineTotal": 162
      }
    ],
    "total": 316.8,
    "currency": "EUR"
  },
  {
    "id": 4,
    "status": "CANCELLED",
    "placedAt": "2026-11-10T18:06:54.175578Z",
    "customerName": "Ada Lovelace",
    "lines": [
      {
        "id": 12,
        "sku": "BK-2",
        "productName": "Java Persistence",
        "quantity": 1,
        "unitPrice": 54,
        "lineTotal": 54
      },
      {
        "id": 13,
        "sku": "BK-1",
        "productName": "Clean Code",
        "quantity": 2,
        "unitPrice": 39.9,
        "lineTotal": 79.8
      }
    ],
    "total": 133.8,
    "currency": "EUR"
  },
  {
    "id": 1,
    "status": "NEW",
    "placedAt": "2026-11-10T18:06:54.131763Z",
    "customerName": "Ada Lovelace",
    "lines": [
      {
        "id": 1,
        "sku": "KB-1",
        "productName": "Office keyboard",
        "quantity": 1,
        "unitPrice": 24.9,
        "lineTotal": 24.9
      },
      {
        "id": 2,
        "sku": "KB-7",
        "productName": "Mechanical keyboard",
        "quantity": 1,
        "unitPrice": 59,
        "lineTotal": 59
      },
      {
        "id": 3,
        "sku": "BK-2",
        "productName": "Java Persistence",
        "quantity": 3,
        "unitPrice": 54,
        "lineTotal": 162
      },
      {
        "id": 4,
        "sku": "BK-3",
        "productName": "Domain-Driven Design",
        "quantity": 3,
        "unitPrice": 61.5,
        "lineTotal": 184.5
      }
    ],
    "total": 430.4,
    "currency": "EUR"
  }
]
```

SQL log (one select with join order_line + join product):

```
HTTP/1.1 404
Content-Type: application/problem+json

{"type":"about:blank","title":"Not Found","status":404,"detail":"No customer with email nobody@example.com","instance":"/api/customers/nobody@example.com/orders"}
```
