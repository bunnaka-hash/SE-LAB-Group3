package edu.itc.shop.order;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import edu.itc.shop.common.InvalidRequestException;
import edu.itc.shop.common.Page;
import edu.itc.shop.customer.CustomerNotFoundException;
import edu.itc.shop.service.InsufficientStockException;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    private static final OrderDto ORDER_17 = new OrderDto(17L, 1L, OrderStatus.NEW,
            OffsetDateTime.parse("2026-10-29T09:06:34.640146Z"), new BigDecimal("120.78"),
            List.of(new OrderLine(1L, 2, new BigDecimal("49.90")),
                    new OrderLine(3L, 1, new BigDecimal("10.00"))));

    @Autowired
    MockMvc mvc;

    @MockitoBean
    OrderService service;

    private ResultActions placeOrder(String json) throws Exception {
        return mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void placeReturns201WithLocationAndOrder() throws Exception {
        when(service.place(any())).thenReturn(ORDER_17);

        placeOrder("""
                {"customerId":1,"lines":[{"productId":1,"qty":2},{"productId":3,"qty":1}]}
                """)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/orders/17"))
                .andExpect(jsonPath("$.id").value(17))
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.total").value(120.78))
                .andExpect(jsonPath("$.lines[0].unitPrice").value(49.90))
                .andExpect(jsonPath("$.lines.length()").value(2));

        verify(service).place(new PlaceOrder(1L, List.of(new PlaceOrder.Line(1L, 2), new PlaceOrder.Line(3L, 1))));
    }

    @Test
    void orderWithoutLinesIs400() throws Exception {
        placeOrder("""
                {"customerId":1,"lines":[]}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasItem("lines: must not be empty")));

        verifyNoInteractions(service);
    }

    @Test
    void zeroQuantityAndMissingFieldsAre400() throws Exception {
        placeOrder("""
                {"lines":[{"productId":1,"qty":0},{"qty":1}]}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(3))
                .andExpect(jsonPath("$.errors", hasItem("customerId: must not be null")))
                .andExpect(jsonPath("$.errors", hasItem("lines[0].qty: must be greater than or equal to 1")))
                .andExpect(jsonPath("$.errors", hasItem("lines[1].productId: must not be null")));

        verifyNoInteractions(service);
    }

    @Test
    void malformedJsonIs400() throws Exception {
        placeOrder("{\"customerId\":")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

        verifyNoInteractions(service);
    }

    @Test
    void duplicateProductIs400() throws Exception {
        when(service.place(any())).thenThrow(new InvalidRequestException("Product 1 appears more than once"));

        placeOrder("""
                {"customerId":1,"lines":[{"productId":1,"qty":2},{"productId":1,"qty":1}]}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Product 1 appears more than once"));
    }

    @Test
    void unknownCustomerIs404() throws Exception {
        when(service.place(any())).thenThrow(new CustomerNotFoundException(9L));

        placeOrder("""
                {"customerId":9,"lines":[{"productId":1,"qty":1}]}
                """)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Customer not found: 9"));
    }

    @Test
    void notEnoughStockIs409ProblemDetail() throws Exception {
        when(service.place(any())).thenThrow(
                new InsufficientStockException("Insufficient stock for product 2: 150 available, 999 requested"));

        placeOrder("""
                {"customerId":1,"lines":[{"productId":1,"qty":2},{"productId":2,"qty":999}]}
                """)
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("Insufficient stock for product 2: 150 available, 999 requested"));
    }

    @Test
    void listReturnsPageJson() throws Exception {
        when(service.list(1L, OrderStatus.NEW, 0, 5)).thenReturn(Page.of(List.of(ORDER_17), 0, 5, 6));

        mvc.perform(get("/api/orders?customerId=1&status=NEW&page=0&size=5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(17))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void listWithoutParametersUsesDefaults() throws Exception {
        when(service.list(null, null, 0, 20)).thenReturn(Page.of(List.of(), 0, 20, 0));

        mvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void pageSizeAbove100Is400() throws Exception {
        mvc.perform(get("/api/orders?size=101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasItem("size: must be less than or equal to 100")));

        verifyNoInteractions(service);
    }

    @Test
    void negativePageIs400() throws Exception {
        mvc.perform(get("/api/orders?page=-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasItem("page: must be greater than or equal to 0")));
    }

    @Test
    void unknownStatusIs400() throws Exception {
        mvc.perform(get("/api/orders?status=SHIPPED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid value 'SHIPPED' for parameter 'status'"));
    }

    @Test
    void getReturnsOrderWithLines() throws Exception {
        when(service.findById(17L)).thenReturn(ORDER_17);

        mvc.perform(get("/api/orders/17"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[1].productId").value(3))
                .andExpect(jsonPath("$.placedAt").value("2026-10-29T09:06:34.640146Z"));
    }

    @Test
    void getUnknownOrderIs404() throws Exception {
        when(service.findById(999L)).thenThrow(new OrderNotFoundException(999L));

        mvc.perform(get("/api/orders/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.instance").value("/api/orders/999"));
    }

    @Test
    void cancelReturnsCancelledOrder() throws Exception {
        var cancelled = new OrderDto(17L, 1L, OrderStatus.CANCELLED, ORDER_17.placedAt(),
                ORDER_17.total(), ORDER_17.lines());
        when(service.cancel(17L)).thenReturn(cancelled);

        mvc.perform(post("/api/orders/17/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancelOrderThatIsNotNewIs409() throws Exception {
        when(service.cancel(1L)).thenThrow(new OrderNotCancellableException(1L, OrderStatus.PAID));

        mvc.perform(post("/api/orders/1/cancel"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Order 1 is PAID; only NEW orders can be cancelled"));
    }
}
