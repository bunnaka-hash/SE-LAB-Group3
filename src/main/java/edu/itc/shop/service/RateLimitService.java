package edu.itc.shop.service;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class RateLimitService {

    private static final int MAX_ATTEMPTS = 5;
    private static final long WINDOW_MILLIS = Duration.ofMinutes(1).toMillis();
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public synchronized long recordAttempt(String ip, String username) {
        long now = System.currentTimeMillis();
        windows.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now);

        String ipKey = "ip:" + ip;
        String usernameKey = "username:" + username.trim().toLowerCase(Locale.ROOT);
        Window ipWindow = getWindow(ipKey, now);
        Window usernameWindow = getWindow(usernameKey, now);

        long retryAfter = Math.max(retryAfter(ipWindow, now), retryAfter(usernameWindow, now));
        if (retryAfter > 0) {
            return retryAfter;
        }

        ipWindow.attempts++;
        usernameWindow.attempts++;
        return 0;
    }

    private Window getWindow(String key, long now) {
        return windows.compute(key, (ignored, current) ->
                current == null || current.expiresAt <= now
                        ? new Window(now + WINDOW_MILLIS)
                        : current);
    }

    private long retryAfter(Window window, long now) {
        if (window.attempts < MAX_ATTEMPTS) {
            return 0;
        }
        return Math.max(1, (window.expiresAt - now + 999) / 1000);
    }

    private static final class Window {
        private final long expiresAt;
        private int attempts;

        private Window(long expiresAt) {
            this.expiresAt = expiresAt;
        }
    }
}
