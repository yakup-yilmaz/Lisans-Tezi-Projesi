package com.tezprojesi.api.repository;

import com.tezprojesi.domain.Order;
import java.util.List;
import java.util.Optional;

public interface OrderRepository {
  Order save(Order order);
  Optional<Order> findById(String id);
  List<Order> findAll();
  void clear();
}
