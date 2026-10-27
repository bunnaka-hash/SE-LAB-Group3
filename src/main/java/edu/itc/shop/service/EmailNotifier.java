package edu.itc.shop.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;

@Service
@Primary
@Profile("!dev")
public class EmailNotifier implements Notifier {
    private static final Logger log = LoggerFactory.getLogger(EmailNotifier.class);

    @Override
    public void send(String to, String text) {
        log.info("EMAIL to={} text={}", to, text);
    }

    @PostConstruct
    void init() { log.info("EmailNotifier ready"); }

    @PreDestroy
    void close() { log.info("EmailNotifier closing"); }
}