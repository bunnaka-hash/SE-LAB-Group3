package edu.itc.shop;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Task 5: CORS, security headers and the stored-XSS demo. Values below are test-only. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "JWT_SECRET=0123456789abcdef0123456789abcdef0123456789abcdef",
        "SHOP_SEED_ADMIN_PASSWORD=test-only-admin-1A",
        "SHOP_SEED_CUSTOMER_PASSWORD=test-only-alice-1A"})
class HardeningTest {

    static final String PAYLOAD = "<img src=x onerror=\\\"document.title='pwned'\\\">";

    @Autowired
    MockMvc mvc;

    @Test
    void preflightAllowsOnlyTheFrontendOrigin() throws Exception {
        mvc.perform(options("/api/orders")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Max-Age", "3600"));

        mvc.perform(options("/api/orders")
                        .header("Origin", "http://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void securityHeadersArePresent() throws Exception {
        mvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Content-Security-Policy",
                        "default-src 'self'; frame-ancestors 'none'; object-src 'none'"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().exists("X-Request-Id"));
    }

    @Test
    void reviewRequiresAuthentication() throws Exception {
        mvc.perform(post("/api/reviews").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":2,\"stars\":3,\"text\":\"ok\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void storedXssIsKeptVerbatimAndRenderedHarmlessly() throws Exception {
        mvc.perform(post("/api/reviews").with(jwt().jwt(j -> j.subject("alice")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":2,\"stars\":1,\"text\":\"" + PAYLOAD + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/products/2/reviews"));

        // (a) JSON API: Jackson string-escapes it, JSON content type, nosniff
        mvc.perform(get("/api/products/2/reviews"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].author").value("alice"))
                .andExpect(jsonPath("$[0].text").value("<img src=x onerror=\"document.title='pwned'\">"));

        // (b) Thymeleaf th:text: the payload appears only as escaped text, never as a tag
        mvc.perform(get("/products/2/reviews.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString(
                                "&lt;img src=x onerror=&quot;document.title=&#39;pwned&#39;&quot;&gt;")))
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("<img"))));
    }

    @Test
    void reviewValidationRejectsBadInput() throws Exception {
        mvc.perform(post("/api/reviews").with(jwt().jwt(j -> j.subject("alice")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":2,\"stars\":6,\"text\":\"x\"}"))
                .andExpect(status().isBadRequest());

        String tooLong = "a".repeat(1001);
        mvc.perform(post("/api/reviews").with(jwt().jwt(j -> j.subject("alice")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":2,\"stars\":3,\"text\":\"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest());
    }
}
