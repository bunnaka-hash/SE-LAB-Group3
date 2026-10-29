package edu.itc.shop.customer;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CustomerController.class)
class CustomerControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    CustomerService service;

    @Test
    void listReturnsCustomers() throws Exception {
        when(service.findAll()).thenReturn(List.of(new Customer(1L, "Sokha Chan", "sokha@example.com")));

        mvc.perform(get("/api/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("sokha@example.com"));
    }

    @Test
    void unknownCustomerIs404() throws Exception {
        when(service.findById(9L)).thenThrow(new CustomerNotFoundException(9L));

        mvc.perform(get("/api/customers/9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Customer not found: 9"));
    }
}
