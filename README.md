# shop-jpa — Lab 03: Hibernate Framework and Spring Data JPA

Spring Boot 4.1.1 · Hibernate ORM 7.4.5 · Flyway · H2 · Java 25.
Flyway owns the schema (`src/main/resources/db/migration`); Hibernate only **validates** it (`ddl-auto: validate`).

Run the app: `./mvnw spring-boot:run` — H2 console at <http://localhost:8080/h2-console>
(JDBC URL `jdbc:h2:mem:shop`, user `sa`, empty password).

## Task 1 — Entities and mappings, validated by Flyway

### Start-up with a correct schema

```text
o.f.core.internal.command.DbMigrate : Current version of schema "PUBLIC": << Empty Schema >>
o.f.core.internal.command.DbMigrate : Migrating schema "PUBLIC" to version "1 - init"
o.f.core.internal.command.DbMigrate : Successfully applied 1 migration to schema "PUBLIC", now at version v1
j.LocalContainerEntityManagerFactoryBean : Initialized JPA EntityManagerFactory for persistence unit 'default'
edu.itc.shop.ShopJpaApplication : Started ShopJpaApplication in 2.0 seconds
```

### Deliberate mismatch 1 — missing column

In `V1__init.sql` only, the `product` column `sku` was renamed to `code` (the entity was left unchanged).
Flyway still applied the migration (the SQL itself is valid), but Hibernate refused to start:

```text
Caused by: org.hibernate.tool.schema.spi.SchemaManagementException:
  Schema validation: missing column [sku] in table [product]
```

Reverted afterwards.

### Deliberate mismatch 2 — sequence increment

In `V1__init.sql` only, `product_seq` was declared `INCREMENT BY 1` while the entity says `allocationSize = 50`:

```text
Caused by: org.hibernate.MappingException:
  The increment size of the [product_seq] sequence is set to [50] in the entity mapping
  but the mapped database sequence increment size is [1]
```

Reverted afterwards. The `INCREMENT BY 50` in the migration must always equal `allocationSize` in the
`@SequenceGenerator`; otherwise Hibernate (pooled optimizer) would hand out colliding ids.

### A third mismatch found along the way — `CHAR(3)` vs `VARCHAR`

The first start-up with the spec's `currency CHAR(3)` failed validation:

```text
Schema validation: wrong column type encountered in column [currency] in table [order_line];
  found [character (Types#CHAR)], but expecting [varchar(3) (Types#VARCHAR)]
```

A Java `String` maps to `VARCHAR` by default. Fixed on the mapping side with
`@JdbcTypeCode(SqlTypes.CHAR)` on `Money.currency`, so the SQL keeps `CHAR(3)`.

### Notes on the model

* `Money` and `Address` are `@Embeddable` records; `Customer.shipping` reuses `Address` with
  `@AttributeOverride` to the `ship_*` columns.
* `Category` and `OrderStatus` use `@Enumerated(EnumType.STRING)`, stored as text, never as ordinals.
* All ids are `Long` with `GenerationType.SEQUENCE`, a named `@SequenceGenerator` and `allocationSize = 50`;
  there is no `setId` anywhere. Entities have `protected` no-arg constructors.
* `ProductMappingTest` (`@DataJpaTest` + `Replace.NONE`) runs Flyway and validation against H2, persists a
  `Product`, flushes and clears, reloads it, and checks via a native query that `category` contains `HARDWARE`.
