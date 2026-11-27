package edu.itc.shop.web;

import edu.itc.shop.config.SecurityConfig;
import edu.itc.shop.service.OrderService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;

@WebMvcTest(OrderController.class)
@Import(SecurityConfig.class)
class OrderControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @Test
    void anonymousUserGets401() throws Exception {
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    @WithMockUser(username = "alice", roles = "CUSTOMER")
    void customerCannotShipOrder() throws Exception {
        doThrow(new org.springframework.security.access.AccessDeniedException(
                "Access denied"))
                .when(orderService).ship(1L);

        mockMvc.perform(post("/api/orders/1/ship"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminCanShipOrder() throws Exception {
        mockMvc.perform(post("/api/orders/1/ship"))
                .andExpect(status().isNoContent());

        verify(orderService).ship(1L);
    }
}
