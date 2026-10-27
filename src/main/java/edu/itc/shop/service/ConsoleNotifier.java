package edu.itc.shop.service;

import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("dev")
@Primary
public class ConsoleNotifier implements Notifier {
    @Override
    public void send(String to, String text) {
        System.out.println("CONSOLE to=" + to + " text=" + text);
    }
}