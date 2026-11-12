package edu.itc.shop.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import edu.itc.shop.domain.*;
import edu.itc.shop.repository.CustomerRepository;
import edu.itc.shop.repository.OrderRepository;
import edu.itc.shop.repository.ProductRepository;
import edu.itc.shop.repository.TagRepository;

/** Run with: .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev" */
@Component
@Profile("dev")
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final CustomerRepository customers;
    private final ProductRepository products;
    private final TagRepository tags;
    private final OrderRepository orders;

    public DataSeeder(CustomerRepository customers, ProductRepository products,
                      TagRepository tags, OrderRepository orders) {
        this.customers = customers;
        this.products = products;
        this.tags = tags;
        this.orders = orders;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (customers.count() > 0) return;   // idempotent

        // --- 3 customers ---
        List<Customer> cs = customers.saveAll(List.of(
                customer("Ada Lovelace",   "ada@example.com",   "12 St James Sq", "London",    "SW1Y"),
                customer("Grace Hopper",   "grace@example.com", "1 Navy Way",     "Arlington", "22202"),
                customer("Linus Torvalds", "linus@example.com", "5 Kernel Rd",    "Portland",  "97201")));

        // --- 4 tags ---
        Map<String, Tag> t = Map.of(
                "sale", new Tag("sale"), "new", new Tag("new"),
                "gift", new Tag("gift"), "eco",  new Tag("eco"));
        tags.saveAll(t.values());

        // --- 12 products with tags ---
        List<Product> ps = new ArrayList<>(List.of(
                product("KB-1", "Office keyboard",       "24.90", Category.HARDWARE, 40, t.get("sale")),
                product("KB-7", "Mechanical keyboard",   "59.00", Category.HARDWARE,  4, t.get("sale"), t.get("new")),
                product("KB-9", "Gaming keyboard",      "129.00", Category.HARDWARE,  0, t.get("new")),
                product("MS-2", "Wireless mouse",        "19.90", Category.HARDWARE,  3, t.get("gift")),
                product("MN-4", "27-inch monitor",      "249.00", Category.HARDWARE, 12),
                product("BK-1", "Clean Code",            "39.90", Category.BOOKS,    25, t.get("gift")),
                product("BK-2", "Java Persistence",      "54.00", Category.BOOKS,     2, t.get("new")),
                product("BK-3", "Domain-Driven Design",  "61.50", Category.BOOKS,     8),
                product("TY-1", "Wooden train set",      "34.00", Category.TOYS,     15, t.get("gift"), t.get("eco")),
                product("TY-2", "Puzzle 1000 pieces",    "14.50", Category.TOYS,      1, t.get("sale")),
                product("FD-1", "Organic coffee beans",  "12.90", Category.FOOD,     60, t.get("eco")),
                product("FD-2", "Dark chocolate box",     "9.90", Category.FOOD,      0, t.get("gift"), t.get("sale"))));
        products.saveAll(ps);

        // --- 30 orders, 2-4 distinct lines each, mixed statuses ---
        Random rnd = new Random(42);
        OrderStatus[] statuses = OrderStatus.values();
        for (int i = 0; i < 30; i++) {
            Order o = new Order(cs.get(i % cs.size()));
            List<Product> shuffled = new ArrayList<>(ps);
            Collections.shuffle(shuffled, rnd);
            int lineCount = 2 + rnd.nextInt(3);
            for (int k = 0; k < lineCount; k++) {
                o.addLine(shuffled.get(k), 1 + rnd.nextInt(3));
            }
            o.setStatus(statuses[i % statuses.length]);
            orders.save(o);
        }
        log.info("Seeded {} customers, {} tags, {} products, {} orders",
                cs.size(), t.size(), ps.size(), orders.count());
    }

    private static Customer customer(String name, String email, String street, String city, String zip) {
        Address a = new Address(street, city, zip);
        return new Customer(name, email, a, a);
    }

    private static Product product(String sku, String name, String price, Category c, int stock, Tag... ts) {
        Product p = new Product(sku, name, Money.eur(price), c, stock);
        for (Tag tag : ts) p.tag(tag);
        return p;
    }
}