package edu.itc.shop;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ShopApiTest extends JerseyTest {

    @Override
    protected jakarta.ws.rs.core.Application configure() {
        return Main.config();
    }

    @Test
    void status200() {
        Response response = target("/products/1").request().get();
        assertEquals(200, response.getStatus());
    }

    @Test
    void status201() {
        Response response = target("/orders").request()
                .header("X-Api-Key", "demo-123")
                .header("Idempotency-Key", "create-201")
                .post(Entity.json("{\"lines\":[{\"productId\":1,\"quantity\":1}]}"));
        assertEquals(201, response.getStatus());
        assertTrue(response.getHeaderString("Location") != null);
        assertTrue(response.getHeaderString("Link") != null);
    }

    @Test
    void idempotencyReplay() {
        String body = "{\"lines\":[{\"productId\":1,\"quantity\":1}]}";
        Response first = target("/orders").request()
                .header("X-Api-Key", "demo-123")
                .header("Idempotency-Key", "replay-test")
                .post(Entity.json(body));
        Response replay = target("/orders").request()
                .header("X-Api-Key", "demo-123")
                .header("Idempotency-Key", "replay-test")
                .post(Entity.json(body));
        assertEquals(201, replay.getStatus());
        assertEquals("true", replay.getHeaderString("Idempotency-Replayed"));
        assertEquals(first.getHeaderString("Location"),
                replay.getHeaderString("Location"));
    }

    @Test
    void status204() {
        Response created = createOrder("cancel-204", 1);
        String location = created.getHeaderString("Location");
        String id = location.substring(location.lastIndexOf('/') + 1);
        Response response = target("/orders/" + id).request()
                .header("X-Api-Key", "demo-123").delete();
        assertEquals(204, response.getStatus());
    }

    @Test
    void status304() {
        Response first = target("/products/1").request().get();
        Response response = target("/products/1").request()
                .header("If-None-Match", first.getHeaderString("ETag")).get();
        assertEquals(304, response.getStatus());
    }

    @Test
    void status400() {
        Response response = target("/orders").request()
                .header("X-Api-Key", "demo-123")
                .header("Idempotency-Key", "bad-400")
                .post(Entity.json("{\"lines\":[{\"productId\":1,\"quantity\":0}]}"));
        assertEquals(400, response.getStatus());
        assertEquals(ProblemMediaType.MEDIA_TYPE,
                response.getMediaType().toString());
    }

    @Test
    void status401() {
        Response response = target("/orders").request()
                .post(Entity.json("{\"lines\":[{\"productId\":1,\"quantity\":1}]}"));
        assertEquals(401, response.getStatus());
    }

    @Test
    void status404() {
        Response response = target("/orders").request()
                .header("X-Api-Key", "demo-123")
                .header("Idempotency-Key", "missing-404")
                .post(Entity.json("{\"lines\":[{\"productId\":999999,\"quantity\":1}]}"));
        assertEquals(404, response.getStatus());
    }

    @Test
    void status406() {
        Response response = target("/products/1").request()
                .header("Accept", "text/plain").get();
        assertEquals(406, response.getStatus());
    }

    @Test
    void status409() {
        Response response = target("/orders").request()
                .header("X-Api-Key", "demo-123")
                .header("Idempotency-Key", "stock-409")
                .post(Entity.json("{\"lines\":[{\"productId\":3,\"quantity\":1}]}"));
        assertEquals(409, response.getStatus());
    }

    @Test
    void status412() {
        Response response = target("/products/1").request()
                .header("X-Api-Key", "demo-123")
                .header("If-Match", "\"stale\"")
                .put(Entity.json("{\"name\":\"Updated\",\"sku\":\"KT-01\",\"price\":24.90,\"stock\":12}"));
        assertEquals(412, response.getStatus());
    }

    @Test
    void status415() {
        Response response = target("/orders").request()
                .header("X-Api-Key", "demo-123")
                .header("Idempotency-Key", "bad-content-415")
                .post(Entity.entity("plain", MediaType.TEXT_PLAIN));
        assertEquals(415, response.getStatus());
    }

    @Test
    void status422() {
        createOrder("same-422", 1);
        Response response = target("/orders").request()
                .header("X-Api-Key", "demo-123")
                .header("Idempotency-Key", "same-422")
                .post(Entity.json("{\"lines\":[{\"productId\":2,\"quantity\":1}]}"));
        assertEquals(422, response.getStatus());
    }

    @Test
    void status428() {
        Response response = target("/orders").request()
                .header("X-Api-Key", "demo-123")
                .post(Entity.json("{\"lines\":[{\"productId\":1,\"quantity\":1}]}"));
        assertEquals(428, response.getStatus());
    }

    private Response createOrder(String key, long productId) {
        return target("/orders").request()
                .header("X-Api-Key", "demo-123")
                .header("Idempotency-Key", key)
                .post(Entity.json("{\"lines\":[{\"productId\":" + productId + ",\"quantity\":1}]}"));
    }

    private static final class ProblemMediaType {
        static final String MEDIA_TYPE = "application/problem+json";
    }
}
