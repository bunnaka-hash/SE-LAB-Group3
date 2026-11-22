
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
