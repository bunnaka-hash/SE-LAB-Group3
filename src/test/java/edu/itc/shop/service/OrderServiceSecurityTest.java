package edu.itc.shop.service;

import edu.itc.shop.domain.Order;
import edu.itc.shop.repository.OrderRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class OrderServiceSecurityTest {

    @Autowired
    private OrderService service;

    @MockitoBean
    private OrderRepository orders;

    @Test
    @WithMockUser(username = "alice", roles = "CUSTOMER")
    void aliceCanReadHerOwnHistory() {
        when(orders.findByCustomer("alice")).thenReturn(List.of());

        assertNotNull(service.history("alice"));

        verify(orders).findByCustomer("alice");
    }

    @Test
    @WithMockUser(username = "alice", roles = "CUSTOMER")
    void aliceCannotReadBobsHistory() {
        assertThrows(
                AccessDeniedException.class,
                () -> service.history("bob")
        );

        verifyNoInteractions(orders);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminCanReadBobsHistory() {
        when(orders.findByCustomer("bob")).thenReturn(List.of());

        assertNotNull(service.history("bob"));

        verify(orders).findByCustomer("bob");
    }

    @Test
    @WithMockUser(username = "alice", roles = "CUSTOMER")
    void aliceCannotShipAnOrder() {
        assertThrows(
                AccessDeniedException.class,
                () -> service.ship(1L)
        );

        verifyNoInteractions(orders);
    }

    @Test
    void unauthenticatedUserCannotAccessHistory() {
        assertThrows(
                AuthenticationCredentialsNotFoundException.class,
                () -> service.history("alice")
        );

        verifyNoInteractions(orders);
    }
}
