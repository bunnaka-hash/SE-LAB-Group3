
package edu.itc.shop.web;

import java.math.BigDecimal;
import java.util.List;

import edu.itc.shop.domain.Product;
import edu.itc.shop.service.ProductService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ProductService service;

    @Test
    void listReturns200() throws Exception {
        when(service.search(null)).thenReturn(List.of(
                new Product(1L, "Keyboard",
                        new BigDecimal("25.00"), 10)));

        mvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Keyboard"));
    }

    @Test
    void missingProductReturns404() throws Exception {
        when(service.findById(999L))
                .thenThrow(new edu.itc.shop.service
                        .ProductNotFoundException(999L));

        mvc.perform(get("/api/products/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void invalidCreateReturns400WithThreeFieldErrors()
            throws Exception {
        mvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"","price":0,"stock":-1}
                    """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(3));

        verifyNoInteractions(service);
    }

    @Test
    void createReturns201AndLocation() throws Exception {
        when(service.create(any())).thenReturn(
                new Product(6L, "USB-C cable",
                        new BigDecimal("7.90"), 25));

        mvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"USB-C cable","price":7.90,"stock":25}
                    """))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        "Location", "/api/products/6"))
                .andExpect(jsonPath("$.id").value(6));
    }
}
