package com.cms.clubmanagementsystem.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class EventBus {
    private static EventBus instance;
    private final Map<String, CopyOnWriteArrayList<Consumer<Object>>> subscribers;

    private EventBus() {
        subscribers = new HashMap<>();
    }

    public static EventBus getInstance() {
        if (instance == null) {
            instance = new EventBus();
        }
        return instance;
    }

    public static void subscribe(String eventType, Consumer<Object> subscriber) {
        getInstance()._subscribe(eventType, subscriber);
    }

    public static void publish(String eventType, Object data) {
        getInstance()._publish(eventType, data);
    }

    public static void publish(String eventType) {
        getInstance()._publish(eventType, null);
    }

    private void _subscribe(String eventType, Consumer<Object> subscriber) {
        subscribers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(subscriber);
    }

    private void _publish(String eventType, Object data) {
        if (subscribers.containsKey(eventType)) {
            subscribers.get(eventType).forEach(subscriber -> {
                try {
                    subscriber.accept(data);
                } catch (Exception e) {
                    System.err.println("Error in event subscriber for " + eventType + ": " + e.getMessage());
                }
            });
        }
    }

    public static void unsubscribe(String eventType, Consumer<Object> subscriber) {
        getInstance()._unsubscribe(eventType, subscriber);
    }

    // NEW METHOD: Unsubscribe all listeners for a specific event type
    public static void unsubscribe(String eventType) {
        getInstance()._unsubscribe(eventType);
    }

    // NEW METHOD: Unsubscribe all listeners (complete cleanup)
    public static void unsubscribeAll() {
        getInstance()._unsubscribeAll();
    }

    private void _unsubscribe(String eventType, Consumer<Object> subscriber) {
        if (subscribers.containsKey(eventType)) {
            subscribers.get(eventType).remove(subscriber);
        }
    }

    // NEW METHOD: Remove all subscribers for an event type
    private void _unsubscribe(String eventType) {
        if (subscribers.containsKey(eventType)) {
            subscribers.get(eventType).clear();
        }
    }

    // NEW METHOD: Remove all subscribers for all event types
    private void _unsubscribeAll() {
        subscribers.values().forEach(CopyOnWriteArrayList::clear);
        subscribers.clear();
    }
}