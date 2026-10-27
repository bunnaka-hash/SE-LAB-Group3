package edu.itc.shop.service;

import java.time.Clock;
import java.time.LocalTime;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class WelcomeService {
    private final Notifier defaultNotifier;
    private final Notifier urgentNotifier;
    private final Clock clock;

    public WelcomeService(Notifier defaultNotifier,
                          @Qualifier("smsNotifier") Notifier urgentNotifier,
                          Clock clock) {
        this.defaultNotifier = defaultNotifier;
        this.urgentNotifier = urgentNotifier;
        this.clock = clock;
    }

    public String welcome(String user, boolean urgent) {
        String text = "Welcome " + user + "! It is " + LocalTime.now(clock).withNano(0);
        Notifier notifier = urgent ? urgentNotifier : defaultNotifier;
        notifier.send(user, text);
        return text;
    }
}