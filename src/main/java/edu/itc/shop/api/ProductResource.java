
package edu.itc.shop.api;

import java.net.URI;
import java.util.List;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import edu.itc.shop.api.params.ProductFilter;
import edu.itc.shop.api.dto.ProductDto;
import edu.itc.shop.api.dto.StockChange;
import edu.itc.shop.domain.Product;
import edu.itc.shop.domain.ProductService;

@Path("/products")
@Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
@Consumes({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
public class ProductResource {
    private final ProductService service;

    @Inject
    public ProductResource(ProductService service) {
        this.service = service;
    }

    // GET /products
    @GET
    public List<ProductDto> list(@BeanParam @Valid ProductFilter f) { // CHANGED
        return service.search(f.q, f.minPrice, f.maxPrice, f.inStock, f.sortField(), f.descending())
                .stream().map(ProductDto::from).toList();
    }

    // GET /products/{id}
    @GET
    @Path("{id: \\d+}")
    public ProductDto get(@PathParam("id") long id) {
        Product product = service.findById(id)
                .orElseThrow(() ->
                    new NotFoundException("Product " + id + " not found")
                );

        return ProductDto.from(product);
    }

    // POST /products
    @POST
    public Response create(
            ProductDto dto,
            @Context UriInfo uriInfo) {

        Product saved = service.create(dto.toProduct(0, 0));

        URI location = uriInfo.getAbsolutePathBuilder()
                .path(Long.toString(saved.id()))
                .build();

        return Response.created(location)
                .entity(ProductDto.from(saved))
                .build();
    }

    // PUT /products/{id}
    @PUT
    @Path("{id: \\d+}")
    public ProductDto update(
            @PathParam("id") long id,
            ProductDto dto) {

        Product updated = service.update(id, dto.toProduct(id, 0))
                .orElseThrow(() ->
                    new NotFoundException("Product " + id + " not found")
                );

        return ProductDto.from(updated);
    }

    // PATCH /products/{id}/stock
    @PATCH
    @Path("{id: \\d+}/stock")
    @Consumes(MediaType.APPLICATION_JSON)
    public ProductDto changeStock(
            @PathParam("id") long id,
            StockChange change) {

        Product updated = service.changeStock(id, change.delta())
                .orElseThrow(() ->
                    new NotFoundException("Product " + id + " not found")
                );

        return ProductDto.from(updated);
    }

    // DELETE /products/{id}
    @DELETE
    @Path("{id: \\d+}")
    public Response delete(@PathParam("id") long id) {

        if (!service.delete(id)) {
            throw new NotFoundException(
                    "Product " + id + " not found"
            );
        }

        return Response.noContent().build();
    }
}