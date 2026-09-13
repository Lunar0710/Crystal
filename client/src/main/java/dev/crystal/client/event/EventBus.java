package dev.crystal.client.event;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class EventBus {

    private final Map<Class<?>, List<Consumer<Object>>> listeners = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public <T> void subscribe(Class<T> eventType, Consumer<T> listener) {
        listeners.computeIfAbsent(eventType, k -> new ArrayList<>())
                 .add((Consumer<Object>) listener);
    }

    public <T> void unsubscribe(Class<T> eventType, Consumer<T> listener) {
        List<Consumer<Object>> list = listeners.get(eventType);
        if (list != null) list.remove(listener);
    }

    public void post(Object event) {
        List<Consumer<Object>> list = listeners.get(event.getClass());
        if (list == null) return;
        for (Consumer<Object> consumer : list) {
            try {
                consumer.accept(event);
            } catch (Exception e) {
                CrystalClient.LOGGER.error("Event handler error for {}: {}", event.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    // Ensure CrystalClient import works
    private static final class CrystalClient {
        static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("crystal");
    }
}
