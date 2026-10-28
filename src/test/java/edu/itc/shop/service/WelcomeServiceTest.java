package edu.itc.shop.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class WelcomeServiceTest {

    /** A fake Notifier that records every call instead of sending anything. */
    static class RecordingNotifier implements Notifier {
        final List<String> calls = new ArrayList<>();

        @Override
        public void send(String to, String text) {
            calls.add(to + "|" + text);
        }
    }

    private final Clock fixed = Clock.fixed(Instant.parse("2026-10-28T09:15:02Z"), ZoneOffset.UTC);

    @Test
    void normalWelcomeUsesDefaultNotifier() {
        var normal = new RecordingNotifier();
        var urgent = new RecordingNotifier();
        var service = new WelcomeService(normal, urgent, fixed);

        String text = service.welcome("ann", false);

        assertEquals("Welcome ann! It is 09:15:02", text);
        assertEquals(List.of("ann|Welcome ann! It is 09:15:02"), normal.calls);
        assertTrue(urgent.calls.isEmpty());
    }

    @Test
    void urgentWelcomeUsesUrgentNotifier() {
        var normal = new RecordingNotifier();
        var urgent = new RecordingNotifier();
        var service = new WelcomeService(normal, urgent, fixed);

        service.welcome("bob", true);

        assertEquals(List.of("bob|Welcome bob! It is 09:15:02"), urgent.calls);
        assertTrue(normal.calls.isEmpty());
    }
}