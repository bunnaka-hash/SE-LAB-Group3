package edu.itc.shop.order;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

import edu.itc.shop.common.Page;
import edu.itc.shop.web.dto.ProductDto;

/**
 * The whole application on a random port, called over real HTTP, on H2 with data-test.sql.
 * Stock is compared before / after, so the tests do not depend on each other's order.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class OrderFlowE2ETest {

    @Autowired
    RestTestClient client;

    private int stockOf(long productId) {
        return client.get().uri("/api/products/{id}", productId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ProductDto.class)
                .returnResult().getResponseBody().stock();
    }

    private long orderCount() {
        return client.get().uri("/api/orders")
                .exchange()
                .expectStatus().isOk()
                .expectBody(Page.class)
                .returnResult().getResponseBody().totalElements();
    }

    private EntityExchangeResult<OrderDto> place(String user, String json) {
        return client.post().uri("/api/orders")
                .header("X-User", user)
                .contentType(MediaType.APPLICATION_JSON)
                .body(json)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(OrderDto.class)
                .returnResult();
    }

    @Test
    void placeThenCancelMovesStockDownAndBackUp() {
        int mouse = stockOf(1);
        int pad = stockOf(3);

        EntityExchangeResult<OrderDto> placed = place("sokha", """
                {"customerId":1,"lines":[{"productId":1,"qty":2},{"productId":3,"qty":1}]}
                """);
        OrderDto order = placed.getResponseBody();

        assertThat(placed.getResponseHeaders().getLocation()).hasToString("/api/orders/" + order.id());
        assertThat(order.status()).isEqualTo(OrderStatus.NEW);
        assertThat(order.total()).isEqualByComparingTo("120.78");      // (2 x 49.90 + 10.00) x 1.10
        assertThat(stockOf(1)).isEqualTo(mouse - 2);
        assertThat(stockOf(3)).isEqualTo(pad - 1);

        client.get().uri("/api/orders/{id}", order.id())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.lines.length()").isEqualTo(2)
                .jsonPath("$.lines[0].unitPrice").isEqualTo(49.90);

        client.post().uri("/api/orders/{id}/cancel", order.id())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("CANCELLED");

        assertThat(stockOf(1)).isEqualTo(mouse);
        assertThat(stockOf(3)).isEqualTo(pad);

        // a second cancel is refused and does not return the stock twice
        client.post().uri("/api/orders/{id}/cancel", order.id())
                .exchange()
                .expectStatus().isEqualTo(409);
        assertThat(stockOf(1)).isEqualTo(mouse);
    }

    @Test
    void lineWithoutEnoughStockIs409AndNothingChanges() {
        int mouse = stockOf(1);
        int cable = stockOf(2);
        long orders = orderCount();

        client.post().uri("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"customerId":1,"lines":[{"productId":1,"qty":2},{"productId":2,"qty":999}]}
                        """)
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.title").isEqualTo("Conflict")
                .jsonPath("$.detail").isEqualTo("Insufficient stock for product 2: " + cable + " available, 999 requested");

        // line 1 was decremented first, then rolled back together with everything else
        assertThat(stockOf(1)).isEqualTo(mouse);
        assertThat(stockOf(2)).isEqualTo(cable);
        assertThat(orderCount()).isEqualTo(orders);
    }

    @Test
    void listFiltersAndPagesOverHttp() {
        // customer 2 has exactly one order in data-test.sql and the other tests never add one
        client.get().uri("/api/orders?customerId=2&status=CANCELLED")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(1)
                .jsonPath("$.content[0].id").isEqualTo(3)
                .jsonPath("$.content[0].lines[0].productId").isEqualTo(5);

        client.get().uri("/api/orders?customerId=1&page=0&size=1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content.length()").isEqualTo(1)
                .jsonPath("$.size").isEqualTo(1)
                .jsonPath("$.page").isEqualTo(0);

        client.get().uri("/api/orders?size=500")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void auditLineHasUserAndRequestId(CapturedOutput output) {
        EntityExchangeResult<OrderDto> placed = place("vanna", """
                {"customerId":3,"lines":[{"productId":4,"qty":1}]}
                """);
        String requestId = placed.getResponseHeaders().getFirst("X-Request-Id");
        long id = placed.getResponseBody().id();

        assertThat(requestId).isNotBlank();
        assertThat(output).contains("AUDIT who=vanna what=OrderService.place(PlaceOrder[customerId=3")
                .contains("outcome=OK result=OrderDto(id=" + id + ")")
                .contains("request=" + requestId)
                .contains("[" + requestId + "]");           // the MDC request id of the log line itself
    }
}
