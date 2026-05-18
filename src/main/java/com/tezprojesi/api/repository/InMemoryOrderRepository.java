package com.tezprojesi.api.repository;

import com.tezprojesi.domain.Order;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemoryOrderRepository implements OrderRepository {

  private final Map<String, Order> storage = new HashMap<>();

  @Override
  public Order save(Order order) {
    storage.put(order.getId(), order);
    return order;
  }

  @Override
  public Optional<Order> findById(String id) {
    return Optional.ofNullable(storage.get(id));
  }

  @Override
  public List<Order> findAll() {
    return Collections.unmodifiableList(new ArrayList<>(storage.values()));
  }

  @Override
  public void clear() {
    storage.clear();
  }
}
