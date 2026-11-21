package edu.itc.shop.api;

import java.util.List;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import edu.itc.shop.domain.Product;
import edu.itc.shop.domain.ProductRepository;

@Path("/products")
@Produces(MediaType.APPLICATION_JSON)
public class ProductResource {
    private final ProductRepository repo;

    @Inject
    public ProductResource(ProductRepository repo) { this.repo = repo; }

    @GET
    public List<Product> list() {
        return repo.findAll();
    }

    @GET
    @Path("{id: \\d+}")
    public Product get(@PathParam("id") long id) {
        return repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Product " + id + " not found"));
    }
}
