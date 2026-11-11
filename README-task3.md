## Task 3 · Repositories, queries, projections

### Boot-time error from a typo in a derived query

I renamed `findByCustomerEmailAndStatusOrderByPlacedAtDesc` to `findByCustomerEmaliAndStatusOrderByPlacedAtDesc` and restarted the app:

```
<paste your exact error here, it looks roughly like:
Caused by: org.springframework.data.core.PropertyReferenceException:
  No property 'emali' found for type 'Customer'; Did you mean 'email'
  Traversed path: Order.customer>
```

Spring Data parses every repository method name when the context starts. A wrong property name stops the application during boot, not at the first call.

### Queries in this task

| Method | Kind | Notes |
|---|---|---|
| `findByCustomerEmailAndStatusOrderByPlacedAtDesc` | derived | joins `customer` |
| `countByStatus`, `findFirstByCustomerIdOrderByPlacedAtDesc` | derived | |
| `existsBySku`, `findTop3ByCategoryOrderByPriceAmountDesc`, `findByTagsName` | derived | `PriceAmount` = `price.amount` |
| `findWithLinesByCustomerEmail` | JPQL fetch join | orders, lines and products in 1 statement |
| `countLinesByStatus` | JPQL aggregate | |
| `findLowStock` | native SQL | |
| `findByStatus → Page<OrderSummary>` | interface projection | only projected columns are selected |
| `findByCategory → Page<ProductView>` | record projection | constructor expression, because `amount` is nested in `price` |
| `findAll(Specification, Pageable)` | Specifications | only non-null filters are combined |

### Interface projection SQL (/api/orders)

```
<paste the select from the log: only id, status, placed_at, c.name>
```
