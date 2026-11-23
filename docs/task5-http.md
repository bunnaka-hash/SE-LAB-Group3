# Lab-04: RESTful Web Services (JAX-RS)

## Task 5 – Pagination, Caching and Versioning

### Overview

Task 5 extends the `shop-rest` RESTful API with pagination, HTTP caching, conditional requests, API versioning, and a product reviews sub-resource.

The implementation uses Jakarta REST 4.0, Jersey 4, Grizzly, and Java 25.

### 1. Pagination and Link Headers

The `GET /api/products` endpoint supports pagination through the `page` and `size` query parameters.

- `page`: Page number (starting from 0).
- `size`: Number of products per page (1–100).
- `X-Total-Count`: Total number of products after filtering.
- `Link`: Provides navigation links (`first`, `prev`, `next`, `last`).

**Test command:**

```bash
curl -i "http://localhost:8080/api/products?page=1&size=10"
```

**Expected result:**
- HTTP 200 OK
- Returns 10 products (if available).
- Includes `X-Total-Count` and `Link` response headers.

### 2. ETag and Conditional GET

The `GET /api/products/{id}` endpoint supports HTTP caching using ETags.

- Returns an ETag based on the product version.
- Includes `Cache-Control: private, max-age=60, no-transform`.
- Returns `304 Not Modified` when the client sends a matching `If-None-Match` header.

**First request:**

```bash
curl -i http://localhost:8080/api/products/1
```

**Expected result:** HTTP 200 OK with an ETag header, for example:

```http
ETag: "1"
Cache-Control: private, no-transform, max-age=60
```

**Conditional request:**

```bash
curl -i http://localhost:8080/api/products/1 \
-H 'If-None-Match: "1"'
```

**Expected result:** HTTP 304 Not Modified.

### 3. PUT Preconditions

The `PUT /api/products/{id}` endpoint uses the `If-Match` header to prevent updating a product with an outdated version.

Three scenarios are tested:

**Case 1: Missing If-Match header**

```bash
curl -i -X PUT http://localhost:8080/api/products/1 \
-H 'X-Api-Key: demo-123' \
-H 'Content-Type: application/json' \
-d '{"name":"Kettle XL","sku":"KT-01","price":29.90,"stock":12}'
```

Expected: `428 Precondition Required`.

**Case 2: Stale ETag**

```bash
curl -i -X PUT http://localhost:8080/api/products/1 \
-H 'X-Api-Key: demo-123' \
-H 'If-Match: "0"' \
-H 'Content-Type: application/json' \
-d '{"name":"Kettle XL","sku":"KT-01","price":29.90,"stock":12}'
```

Expected: `412 Precondition Failed`.

**Case 3: Correct ETag**

```bash
curl -i -X PUT http://localhost:8080/api/products/1 \
-H 'X-Api-Key: demo-123' \
-H 'If-Match: "1"' \
-H 'Content-Type: application/json' \
-d '{"name":"Kettle XL","sku":"KT-01","price":29.90,"stock":12}'
```

Expected: `200 OK` with the updated product and a new ETag (`"2"`).

### 4. API Versioning (V1 and V2)

The API supports two product representations selected through the `Accept` media type.

**V1 – Default representation:**

```bash
curl -i http://localhost:8080/api/products/1 \
-H 'Accept: application/vnd.shop.v1+json'
```

Returns the standard product representation:

```json
{
  "id": 1,
  "name": "Kettle XL",
  "sku": "KT-01",
  "price": 29.90,
  "stock": 12
}
```

**V2 – Extended representation:**

```bash
curl -i http://localhost:8080/api/products/1 \
-H 'Accept: application/vnd.shop.v2+json'
```

Returns the extended representation, including `Money` and `available`:

```json
{
  "id": 1,
  "name": "Kettle XL",
  "sku": "KT-01",
  "price": {
    "amount": 29.90,
    "currency": "EUR"
  },
  "stock": 12,
  "available": true
}
```

### 5. Product Reviews Sub-resource

The API provides a reviews sub-resource under each product:

- `GET /api/products/{id}/reviews`: Retrieves all reviews for a product.
- `POST /api/products/{id}/reviews`: Creates a new review.

**Create a review:**

```bash
curl -i -X POST http://localhost:8080/api/products/1/reviews \
-H 'X-Api-Key: demo-123' \
-H 'Content-Type: application/json' \
-d '{"stars":5,"author":"Ana","text":"Boils fast."}'
```

Expected:
- HTTP 201 Created.
- A `Location` header pointing to the newly created review.

**Retrieve reviews:**

```bash
curl -i http://localhost:8080/api/products/1/reviews
```

Expected: HTTP 200 OK with the product's reviews.

### 6. Review Validation

The review creation endpoint validates the request body using Bean Validation.

Validation rules:
- `stars`: Must be between 1 and 5.
- `author`: Must not be blank.
- `text`: Maximum length of 500 characters.

Invalid requests return HTTP 400 Bad Request with an `application/problem+json` response.

**Test invalid review:**

```bash
curl -i -X POST http://localhost:8080/api/products/1/reviews \
-H 'X-Api-Key: demo-123' \
-H 'Content-Type: application/json' \
-d '{"stars":6,"author":"","text":"Invalid review"}'
```

Expected: HTTP 400 Bad Request.

### 7. Unknown Product – 404 Not Found

When accessing reviews for a product that does not exist, the sub-resource locator returns HTTP 404 Not Found.

**Test command:**

```bash
curl -i http://localhost:8080/api/products/999/reviews
```

Expected:
- HTTP 404 Not Found.
- An `application/problem+json` error response.

---

### Running the Application

Start the application:

```bash
mvn -q compile exec:java
```

The server runs at:

```text
http://localhost:8080/api/
```

### Technologies Used

- Java 25
- Jakarta REST 4.0
- Jersey 4
- Grizzly HTTP Server
- Maven
- JSON-B / JAXB
- Bean Validation
- HTTP ETag and Cache-Control

### Task 5 Summary

Task 5 demonstrates advanced REST API concepts, including pagination, HTTP caching, conditional requests, API versioning, and nested resources. It improves API performance, consistency, and maintainability while following RESTful design principles.