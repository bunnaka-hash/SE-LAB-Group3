package edu.itc.shop;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class HashPeekTest {
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void peek() {
        jdbc.queryForList("select id, username, enabled from users")
                .forEach(System.out::println);
    }
}