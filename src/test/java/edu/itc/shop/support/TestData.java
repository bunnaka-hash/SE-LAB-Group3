package edu.itc.shop.support;

import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import edu.itc.shop.domain.*;
import edu.itc.shop.repository.*;

/** Test helper. Unique values (UUID) so tests never clash on the unique columns. */
@Component
public class TestData {

    private final CustomerRepository customers;
    private final ProductRepository products;
    private final OrderRepository orders;

    public TestData(CustomerRepository customers, ProductRepository products,
                    OrderRepository orders) {
        this.customers = customers;
        this.products = products;
        this.orders = orders;
    }

    @Transactional
    public Long newProductId(int stock) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        Product p = new Product("T-" + id, "Test product " + id,
                Money.eur("10.00"), Category.HARDWARE, stock);
        return products.save(p).getId();
    }

    /** Creates a customer, a product and an order (status NEW, version 0) with one line. */
    @Transactional
    public Long newOrder() {
        String id = UUID.randomUUID().toString().substring(0, 8);
        Address address = new Address("1 Test Street", "Phnom Penh", "12000");
        Customer c = customers.save(
                new Customer("Test " + id, id + "@itc.edu", address, address));
        Product p = products.save(new Product("O-" + id, "Order product " + id,
                Money.eur("10.00"), Category.BOOKS, 10));
        Order o = new Order(c);
        o.addLine(p, 1);
        return orders.save(o).getId();
    }
}