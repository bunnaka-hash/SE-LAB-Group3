package edu.itc.shop.api;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import edu.itc.shop.domain.Order;
import edu.itc.shop.domain.Line;
import edu.itc.shop.domain.OrderService;
import edu.itc.shop.domain.PlaceOrder;
import edu.itc.shop.domain.OrderIdempotencyStore;
import edu.itc.shop.api.dto.OrderDto;
import edu.itc.shop.api.dto.Problem;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Link;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HexFormat;

@Path("/orders")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Orders", description = "Place, pay and cancel orders")
public class OrderResource {

    private final OrderService orders;
    private final OrderIdempotencyStore idempotency;

    @Inject
    public OrderResource(OrderService orders, OrderIdempotencyStore idempotency) {
        this.orders = orders;
        this.idempotency = idempotency;
    }

    public OrderResource(OrderService orders) {
        this(orders, new OrderIdempotencyStore());
    }

    @POST
    @Operation(summary = "Place an order")
    @APIResponses({
        @APIResponse(responseCode = "201", description = "Order created"),
        @APIResponse(responseCode = "400", description = "Invalid request"),
        @APIResponse(responseCode = "404", description = "Unknown product"),
        @APIResponse(responseCode = "409", description = "Insufficient stock"),
        @APIResponse(responseCode = "422", description = "Idempotency body mismatch"),
        @APIResponse(responseCode = "428", description = "Idempotency key missing")
    })
    public Response create(
            @Valid @NotNull PlaceOrder command,
            @Parameter(in = ParameterIn.HEADER, required = true,
                    description = "Unique key for request replay")
            @HeaderParam("Idempotency-Key") String idempotencyKey,
            @Context UriInfo uriInfo) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            Problem problem = new Problem("about:blank", "Precondition Required", 428,
                    "Idempotency-Key header is required",
                    uriInfo.getRequestUri().getPath(), null);
            return Response.status(428).type(Problem.MEDIA_TYPE).entity(problem).build();
        }

        String bodyHash = sha256(command);
        OrderIdempotencyStore.Entry existing = idempotency.find(idempotencyKey);
        if (existing != null && !existing.bodyHash().equals(bodyHash)) {
            Problem problem = new Problem("about:blank", "Unprocessable Entity", 422,
                    "Idempotency-Key was already used with a different body",
                    uriInfo.getRequestUri().getPath(), null);
            return Response.status(422).type(Problem.MEDIA_TYPE).entity(problem).build();
        }

        OrderIdempotencyStore.Result result = idempotency.getOrCreate(
                idempotencyKey, bodyHash, () -> orders.place(command));

        return createdResponse(result.entry().order(), uriInfo, result.replayed());
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Get an order")
    @APIResponse(responseCode = "200", description = "Order found")
    @APIResponse(responseCode = "404", description = "Unknown order")
    public Response get(
            @Parameter(description = "Order identifier", required = true)
            @PathParam("id") String id,
            @Context UriInfo uriInfo) {

        Order order = orders.get(id);
        return Response.ok(orderDto(order, uriInfo))
                .links(orderLinks(order, uriInfo))
                .build();
    }

    @POST
    @Path("/{id}/pay")
    public Response pay(@PathParam("id") String id,
                        @Context UriInfo uriInfo) {
        Order order = orders.pay(id);
        return Response.ok(orderDto(order, uriInfo))
                .links(orderLinks(order, uriInfo))
                .build();
    }

    @POST
    @Path("/{id}/cancel")
    public Response cancel(@PathParam("id") String id,
                           @Context UriInfo uriInfo) {
        orders.cancel(id);
        Order order = orders.get(id);
        return Response.status(204)
                .links(orderLinks(order, uriInfo))
                .build();
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") String id,
                           @Context UriInfo uriInfo) {
        orders.cancel(id);
        return Response.noContent()
                .links(orderLinks(orders.get(id), uriInfo))
                .build();
    }

    @PUT
    @Path("/{id}/payment")
    public Response payment(@PathParam("id") String id,
                            @Context UriInfo uriInfo) {
        return pay(id, uriInfo);
    }

    private Response createdResponse(Order order, UriInfo uriInfo, boolean replayed) {
        URI location = uriInfo.getAbsolutePathBuilder()
                .path(order.id())
                .build();
        Response.ResponseBuilder builder = Response.created(location)
                .entity(orderDto(order, uriInfo))
                .links(orderLinks(order, uriInfo));
        if (replayed) {
            builder.header("Idempotency-Replayed", "true");
        }
        return builder.build();
    }

    private OrderDto orderDto(Order order, UriInfo uriInfo) {
        Map<String, String> productLinks = new LinkedHashMap<>();
        boolean first = true;
        for (Line line : order.lines()) {
            String productUrl = uriInfo.getBaseUriBuilder()
                    .path("products")
                    .path(Long.toString(line.getProductId()))
                    .build()
                    .toString();
            if (first) {
                productLinks.put("product", productUrl);
                first = false;
            }
            productLinks.put("product:" + line.getProductId(), productUrl);
        }

        String self = uriInfo.getBaseUriBuilder()
                .path("orders")
                .path(order.id())
                .build()
                .toString();
        String pay = uriInfo.getBaseUriBuilder()
                .path("orders")
                .path(order.id())
                .path("payment")
                .build()
                .toString();
        String cancel = uriInfo.getBaseUriBuilder()
                .path("orders")
                .path(order.id())
                .build()
                .toString();
        return OrderDto.withLinks(order, self, pay, cancel, productLinks);
    }

    private Link[] orderLinks(Order order, UriInfo uriInfo) {
        String orderUrl = uriInfo.getBaseUriBuilder()
                .path("orders")
                .path(order.id())
                .build()
                .toString();
        java.util.ArrayList<Link> links = new java.util.ArrayList<>();
        links.add(Link.fromUri(orderUrl).rel("self").build());
        if (order.status().name().equals("NEW")) {
            links.add(Link.fromUri(orderUrl + "/payment").rel("pay").build());
            links.add(Link.fromUri(orderUrl).rel("cancel").build());
        }
        for (Line line : order.lines()) {
            links.add(Link.fromUri(uriInfo.getBaseUriBuilder()
                    .path("products")
                    .path(Long.toString(line.getProductId()))
                    .build())
                    .rel("product")
                    .build());
        }
        return links.toArray(Link[]::new);
    }

    private static String sha256(PlaceOrder command) {
        StringBuilder body = new StringBuilder();
        if (command != null && command.lines() != null) {
            for (Line line : command.lines()) {
                body.append(line.getProductId())
                        .append(':')
                        .append(line.getQuantity())
                        .append(';');
            }
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(body.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}