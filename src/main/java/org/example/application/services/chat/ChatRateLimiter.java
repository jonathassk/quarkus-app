package org.example.application.services.chat;

import jakarta.enterprise.context.ApplicationScoped;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@ApplicationScoped
public class ChatRateLimiter {

    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();

    public boolean tryAcquire(String key, int maxRequests, Duration window) {
        Instant now = Instant.now();
        Window current =
                windows.compute(
                        key,
                        (k, existing) -> {
                            if (existing == null || existing.isExpired(now, window)) {
                                return new Window(now, 1);
                            }
                            existing.count++;
                            return existing;
                        });
        return current.count <= maxRequests;
    }

    public int retryAfterSeconds(String key, Duration window) {
        Window current = windows.get(key);
        if (current == null) {
            return 1;
        }
        long elapsed = Duration.between(current.windowStart, Instant.now()).getSeconds();
        long remaining = window.getSeconds() - elapsed;
        return (int) Math.max(1, remaining);
    }

    public void recordViolation(String key, int maxRequests, Duration window) {
        tryAcquire(key, maxRequests, window);
    }

    private static final class Window {
        private final Instant windowStart;
        private int count;

        private Window(Instant windowStart, int count) {
            this.windowStart = windowStart;
            this.count = count;
        }

        private boolean isExpired(Instant now, Duration window) {
            return Duration.between(windowStart, now).compareTo(window) >= 0;
        }
    }
}
