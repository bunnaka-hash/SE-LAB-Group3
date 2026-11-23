package edu.itc.shop.api;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Path("/openapi")
@Produces("application/yaml")
public class OpenApiResource {

    @GET
    public Response get() {
        try (InputStream input = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("META-INF/openapi.yaml")) {
            if (input == null) {
                return Response.status(404).build();
            }
            return Response.ok(new String(input.readAllBytes(), StandardCharsets.UTF_8))
                    .type("application/yaml")
                    .build();
        } catch (Exception ex) {
            return Response.serverError().build();
        }
    }
}
