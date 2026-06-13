package com.example.speed.event;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class EventManager {
    private static final List<Consumer<Event>> listeners = new ArrayList<>();
    public static void register(Consumer<Event> listener) { listeners.add(listener); }
    public static void post(Event event) {
        for (Consumer<Event> listener : listeners) listener.accept(event);
    }
}
