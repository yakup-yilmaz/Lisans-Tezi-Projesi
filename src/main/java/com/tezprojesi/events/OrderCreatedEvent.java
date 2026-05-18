package com.tezprojesi.events;

import java.util.Objects;

public final class OrderCreatedEvent implements Event {

  private final String orderId;
  private final String customerNodeId;
  private final double volume;

  public OrderCreatedEvent(String orderId, String customerNodeId, double volume) {
    this.orderId = Objects.requireNonNull(orderId, "orderId must not be null");
    this.customerNodeId = Objects.requireNonNull(customerNodeId, "customerNodeId must not be null");
    if (volume <= 0) {
      throw new IllegalArgumentException("volume must be positive");
    }
    this.volume = volume;
  }

  public String getOrderId() {
    return orderId;
  }

  public String getCustomerNodeId() {
    return customerNodeId;
  }

  public double getVolume() {
    return volume;
  }
}
