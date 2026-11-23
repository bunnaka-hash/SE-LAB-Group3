package edu.itc.shop.domain;

import jakarta.inject.Singleton;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

@Singleton
public class OrderIdempotencyStore {

    public record Entry(String bodyHash, Order order) {
    }
    public record Result(Entry entry, boolean replayed) {
    }

    private final ConcurrentMap<String, Entry> entries = new ConcurrentHashMap<>();

    public Entry find(String key) {
        return entries.get(key);
    }

    public Entry compute(String key, String bodyHash, Supplier<Order> placeOrder) {
        return entries.computeIfAbsent(key, ignored ->
                new Entry(bodyHash, placeOrder.get()));
    }

    public Result getOrCreate(String key, String bodyHash, Supplier<Order> placeOrder) {
        Entry existing = entries.get(key);
        if (existing != null) {
            return new Result(existing, true);
        }
        final boolean[] created = {false};
        Entry entry = entries.computeIfAbsent(key, ignored -> {
            created[0] = true;
            return new Entry(bodyHash, placeOrder.get());
        });
        return new Result(entry, !created[0]);
    }
}
