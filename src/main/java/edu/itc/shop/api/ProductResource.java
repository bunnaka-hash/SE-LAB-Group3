
package edu.itc.shop.api;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import edu.itc.shop.api.dto.ProductV2;
import edu.itc.shop.domain.Money;
import edu.itc.shop.domain.ReviewRepository;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.EntityTag;
import jakarta.ws.rs.core.GenericEntity;
import jakarta.ws.rs.core.Link;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import edu.itc.shop.api.dto.ProductDto;
import edu.itc.shop.api.dto.StockChange;
import edu.itc.shop.api.filter.Compress;
import edu.itc.shop.api.params.ProductFilter;
import edu.itc.shop.domain.Product;
import edu.itc.shop.domain.ProductService;

import jakarta.ws.rs.core.Request;


@Path("/products")
@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
public class ProductResource {

    private final ProductService service;
    private final ReviewRepository reviewRepository;

    @Inject
    public ProductResource(ProductService service, ReviewRepository reviewRepository) {
        this.service = service;
        this.reviewRepository = reviewRepository;
    }

    // Task 5: pagination
    @GET
    @Compress
    public Response list(
            @BeanParam @Valid ProductFilter f,
            @Context UriInfo uriInfo) {

        List<Product> all = service.search(
                f.q, f.minPrice, f.maxPrice,
                f.inStock, f.sortField(), f.descending());

        int total = all.size();
        int lastPage = Math.max(0, (total - 1) / f.size);

        List<ProductDto> page = all.stream()
                .skip((long) f.page * f.size)
                .limit(f.size)
                .map(ProductDto::from)
                .toList();

        List<Link> links = new ArrayList<>();
        links.add(link(uriInfo, 0, "first"));

        if (f.page > 0) {
            links.add(link(uriInfo, f.page - 1, "prev"));
        }

        if (f.page < lastPage) {
            links.add(link(uriInfo, f.page + 1, "next"));
        }

        links.add(link(uriInfo, lastPage, "last"));

        return Response.ok(new GenericEntity<List<ProductDto>>(page) {})
                .links(links.toArray(Link[]::new))
                .header("X-Total-Count", total)
                .build();
    }

    private static Link link(UriInfo uriInfo, int page, String rel) {
        return Link.fromUriBuilder(
                uriInfo.getRequestUriBuilder()
                        .replaceQueryParam("page", page))
                .rel(rel)
                .build();
    }

    
    // Task 5: Product API v1 (JSON and XML)
    @GET
    @Compress
    @Path("{id: \\d+}")
    @Produces({
        "application/json; qs=1.0",
        "application/vnd.shop.v1+json",
        MediaType.APPLICATION_XML
    })
    public Response get(
            @PathParam("id") long id,
            @Context Request request) {

        Product product = service.find(id);

        EntityTag tag = new EntityTag(
                String.valueOf(product.version())
        );

        Response.ResponseBuilder precondition =
                request.evaluatePreconditions(tag);

        if (precondition != null) {
            return precondition.tag(tag).build();
        }

        return Response.ok(ProductDto.from(product))
                .tag(tag)
                .header("Cache-Control", "private, max-age=60, no-transform")
                .build();
    }


    // Task 5: Product API v2
    @GET
    @Path("{id: \\d+}")
    @Produces("application/vnd.shop.v2+json")
    public ProductV2 getV2(@PathParam("id") long id) {

        Product product = service.find(id);

        return new ProductV2(
                product.id(),
                product.name(),
                product.sku(),
                new Money(product.price(), "EUR"),
                product.stock(),
                product.stock() > 0
        );
    }

    @POST
    public Response create(
            @Valid @NotNull ProductDto dto,
            @Context UriInfo uriInfo) {

        Product saved = service.create(dto.toProduct(0, 0));
        URI location = uriInfo.getAbsolutePathBuilder()
                .path(Long.toString(saved.id()))
                .build();

        return Response.created(location)
                .entity(ProductDto.from(saved))
                .build();
    }

    
@PUT
@Path("{id: \\d+}")
public Response replace(
        @PathParam("id") long id,
        @Valid @NotNull ProductDto dto,
        @HeaderParam("If-Match") String ifMatch,
        @Context Request request) {

    // 1. Require If-Match
    if (ifMatch == null || ifMatch.isBlank()) {
        return Response.status(428)
                .type("application/problem+json")
                .entity(java.util.Map.of(
                        "type", "about:blank",
                        "title", "Precondition Required",
                        "status", 428,
                        "detail", "If-Match header is required"))
                .build();
    }

    // 2. Get current product and its ETag
    Product current = service.find(id);
    EntityTag currentTag = new EntityTag(
            String.valueOf(current.version()));

    // 3. Check If-Match
    Response.ResponseBuilder precondition =
            request.evaluatePreconditions(currentTag);

    if (precondition != null) {
        return precondition
                .type("application/problem+json")
                .entity(java.util.Map.of(
                        "type", "about:blank",
                        "title", "Precondition Failed",
                        "status", 412,
                        "detail", "Product has been modified"))
                .build();
    }

    // 4. Update and return the new ETag
    Product saved = service.replace(id, dto.toProduct(id, 0));

    return Response.ok(ProductDto.from(saved))
            .tag(new EntityTag(String.valueOf(saved.version())))
            .build();
}


    @PATCH
    @Path("{id: \\d+}/stock")
    @Consumes(MediaType.APPLICATION_JSON)
    public ProductDto adjustStock(
            @PathParam("id") long id,
            @Valid @NotNull StockChange change) {

        return ProductDto.from(
                service.adjustStock(id, change.delta()));
    }

    @DELETE
    @Path("{id: \\d+}")
    public Response delete(@PathParam("id") long id) {
        service.delete(id);
        return Response.noContent().build();
    }
    // Reviews subresource locator
    @Path("{id: \\d+}/reviews")
    public ReviewResource reviews(@PathParam("id") long id) {
        Product product = service.find(id);
        return new ReviewResource(product, reviewRepository);
    }
}
