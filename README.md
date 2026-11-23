
# Lab 04 – RESTful Web Services (JAX-RS)
hello guy
## Project Information
- Course: Software Engineering
- Project: shop-rest
- Group ID: edu.itc
- Artifact ID: shop-rest
- Java: 25
- Framework: Jakarta REST 4.0 / Jersey 4
- Server: Grizzly

## Description
A product catalogue REST API developed using Jakarta REST (JAX-RS),
Eclipse Jersey, and an embedded Grizzly HTTP server.

## Run the Application
```bash
mvn -q compile exec:java
```

## Base URL
http://localhost:8080/api/

## Tasks
- Task 1: First resource and server
- Task 2: Full CRUD, JSON and XML
- Task 3: Parameters and validation
- Task 4: Providers and filters
- Task 5: Pagination, caching and versioning

## Design note
`ProductResource` is instantiated per request, but it has no mutable fields (only a `final` injected repository) because all state lives in the single shared repository, so nothing in the resource can be corrupted if its lifecycle later changes to a singleton or requests run concurrently.

## JAX-RS vs Spring MVC

### Lab 04: Jakarta REST

```java
@GET
@Path("/products/{id}")
public Response get(@PathParam("id") long id) {
    Product product = service.find(id);
    return Response.ok(ProductDto.from(product)).build();
}
```

```java
@POST
public Response create(@Valid @NotNull ProductDto dto,
                       @Context UriInfo uriInfo) {
    Product saved = service.create(dto.toProduct(0, 0));
    URI location = uriInfo.getAbsolutePathBuilder()
            .path(Long.toString(saved.id())).build();
    return Response.created(location).entity(ProductDto.from(saved)).build();
}
```

### Lab 02: Spring MVC

```java
@GetMapping("/products/{id}")
public ProductDto get(@PathVariable long id) {
    return productService.find(id);
}
```

```java
@PostMapping("/products")
public ResponseEntity<ProductDto> create(
        @Valid @RequestBody ProductDto dto,
        UriComponentsBuilder uriBuilder) {
    ProductDto saved = productService.create(dto);
    URI location = uriBuilder.path("/products/{id}")
            .buildAndExpand(saved.id()).toUri();
    return ResponseEntity.created(location).body(saved);
}
```

| Area | JAX-RS (Lab 04) | Spring MVC (Lab 02) |
|---|---|---|
| Route annotations | `@Path`, `@GET`, `@POST` | `@RequestMapping`, `@GetMapping`, `@PostMapping` |
| Body binding | `@Consumes` plus method parameter objects | `@RequestBody` |
| Path parameters | `@PathParam` | `@PathVariable` |
| Response building | `jakarta.ws.rs.core.Response` | `ResponseEntity` |
| Errors | Exception mappers and `Problem` responses | `@ExceptionHandler` / `@RestControllerAdvice` |
| Filters/interceptors | Container and writer filters/interceptors | Servlet filters and MVC interceptors |
| JSON library | JSON-B / Jersey JSON binding | Jackson by default |
| Dependency injection | Jakarta `@Inject` plus HK2 binder | Spring `@Autowired` / constructor injection |
| Testing | `JerseyTest` plus `Main.config()` | Spring test context / `MockMvc` |
