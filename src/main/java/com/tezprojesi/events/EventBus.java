package com.tezprojesi.events;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class EventBus {

  private final List<Consumer<Event>> subscribers = new CopyOnWriteArrayList<>();

  public void subscribe(Consumer<Event> consumer) {
    subscribers.add(Objects.requireNonNull(consumer, "consumer must not be null"));
  }

  public void publish(Event event) {
    Objects.requireNonNull(event, "event must not be null");
    for (Consumer<Event> subscriber : List.copyOf(subscribers)) {
      subscriber.accept(event);
    }
  }

  public List<Consumer<Event>> getSubscribers() {
    return Collections.unmodifiableList(subscribers);
  }
}
