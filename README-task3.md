## Task 3 · Repositories, queries, projections

### Boot-time error from a wrong property in a derived query

Spring Data parses every repository method name when the application context starts.
While `Product` had no `tags` field yet, the derived query `ProductRepository.findByTagsName(String)`
stopped the application during boot, before any request was handled:

```
Caused by: org.springframework.beans.factory.BeanCreationException: Error creating bean with name
  'productRepository' defined in edu.itc.shop.repository.ProductRepository: No property 'tagsName' found for type 'Product'
Caused by: org.springframework.data.repository.query.QueryCreationException: Cannot create query for method
  [ProductRepository.findByTagsName(java.lang.String)]; No property 'tagsName' found for type 'Product'
Caused by: org.springframework.data.core.PropertyReferenceException: No property 'tagsName' found for type 'Product'
```

The same happens with a plain typo such as `findByCustomerEmaliAndStatus…`:
the error names the unknown property (`emali`) and the type where it was looked up (`Customer`).
A wrong name is therefore caught at startup, never at the first call.

### Queries in this task

| Method | Kind | Notes |
|---|---|---|
| `findByCustomerEmailAndStatusOrderByPlacedAtDesc` | derived | joins `customer` |
| `countByStatus`, `findFirstByCustomerIdOrderByPlacedAtDesc` | derived | |
| `existsBySku`, `findTop3ByCategoryOrderByPriceAmountDesc`, `findByTagsName` | derived | `PriceAmount` = `price.amount`; `findByTagsName` enabled once `Tag` exists (Task 2) |
| `findWithLinesByCustomerEmail` | JPQL fetch join | orders, lines and products in 1 statement |
| `countLinesByStatus` | JPQL aggregate | |
| `findLowStock` | native SQL | |
| `findByStatus → Page<OrderSummary>` | interface projection | only projected columns are selected |
| `findByCategory → Page<ProductView>` | record projection | constructor expression, because `amount` is nested in `price` |
| `findAll(Specification, Pageable)` | Specifications | only non-null filters are combined |

### Interface projection SQL (/api/orders)

Only the four projected columns are selected, not the whole `Order` entity:

```
select o1_0.id,o1_0.status,o1_0.placed_at,c1_0.name from orders o1_0
  join customer c1_0 on c1_0.id=o1_0.customer_id
 where o1_0.status=? order by o1_0.placed_at desc fetch first ? rows only
select count(o1_0.id) from orders o1_0 where o1_0.status=?
```