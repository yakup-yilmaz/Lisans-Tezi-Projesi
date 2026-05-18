package com.tezprojesi.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class Order {

  private final String id;
  private final String customerNodeId;
  private String microDepotNodeId;
  private final List<OrderLine> items;
  private final Instant createdAt;
  private long creationTick;
  private int priorityLevel;
  private OrderStatus status;
  private String assignedVehicleId;
  private final Map<String, Integer> reservedQuantities;

  public Order(String id, String customerNodeId, double demandVolume) {
    this(id, customerNodeId, List.of(new OrderLine("GENERIC", 1, demandVolume)));
  }

  public Order(String id, String customerNodeId, List<OrderLine> items) {
    this(id, customerNodeId, items, 0L, 0);
  }

  public Order(String id, String customerNodeId, List<OrderLine> items, long creationTick, int priorityLevel) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.customerNodeId = Objects.requireNonNull(customerNodeId, "customerNodeId must not be null");
    this.microDepotNodeId = null;
    this.items = List.copyOf(Objects.requireNonNull(items, "items"));
    if (items.isEmpty()) {
      throw new IllegalArgumentException("items must not be empty");
    }
    this.createdAt = Instant.now();
    this.creationTick = Math.max(0L, creationTick);
    this.priorityLevel = Math.max(0, priorityLevel);
    this.status = OrderStatus.CREATED;
    this.assignedVehicleId = null;
    this.reservedQuantities = new LinkedHashMap<>();
  }

  public String getId() {
    return id;
  }

  public String getCustomerNodeId() {
    return customerNodeId;
  }

  public String getMicroDepotNodeId() {
    return microDepotNodeId;
  }

  public void assignMicroDepot(String microDepotNodeId) {
    if (this.microDepotNodeId != null) {
      throw new IllegalStateException("Micro depot already assigned: " + this.microDepotNodeId);
    }
    this.microDepotNodeId = Objects.requireNonNull(microDepotNodeId, "microDepotNodeId must not be null");
  }

  public List<OrderLine> getItems() {
    return items;
  }

  public double getDemandVolume() {
    return items.stream().mapToDouble(OrderLine::getLineVolume).sum();
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public long getCreationTick() {
    return creationTick;
  }

  public void setCreationTick(long creationTick) {
    this.creationTick = Math.max(0L, creationTick);
  }

  public int getPriorityLevel() {
    return priorityLevel;
  }

  public void setPriorityLevel(int priorityLevel) {
    this.priorityLevel = Math.max(0, priorityLevel);
  }

  public boolean isHighPriority() {
    return priorityLevel > 0;
  }

  public OrderStatus getStatus() {
    return status;
  }

  public String getAssignedVehicleId() {
    return assignedVehicleId;
  }

  public void assignVehicle(String vehicleId) {
    this.assignedVehicleId = Objects.requireNonNull(vehicleId, "vehicleId must not be null");
    this.status = OrderStatus.ASSIGNED;
  }

  public void waitForReplenishment() {
    this.status = OrderStatus.WAITING_REPLENISHMENT;
  }

  public void fulfillFromMicroStock() {
    this.status = OrderStatus.FULFILLED_FROM_MICRO;
  }

  public void markInTransit() {
    if (status != OrderStatus.ASSIGNED && status != OrderStatus.WAITING_REPLENISHMENT) {
      throw new IllegalStateException("Cannot mark in transit unless assigned or waiting replenishment");
    }
    this.status = OrderStatus.IN_TRANSIT;
  }

  public void complete() {
    if (status != OrderStatus.IN_TRANSIT && status != OrderStatus.FULFILLED_FROM_MICRO) {
      throw new IllegalStateException("Cannot complete unless fulfilled from micro stock or in transit");
    }
    this.status = OrderStatus.COMPLETED;
  }

  public void completeWithMicroFulfillment() {
    if (status == OrderStatus.WAITING_REPLENISHMENT) {
      fulfillFromMicroStock();
    }
    complete();
  }

  public void fail() {
    this.status = OrderStatus.FAILED;
  }

  public void reserveItems(List<OrderLine> reservedItems) {
    for (OrderLine item : reservedItems) {
      if (item.getQuantity() <= 0) {
        continue;
      }
      int ordered = items.stream()
          .filter(i -> i.getProductId().equals(item.getProductId()))
          .mapToInt(OrderLine::getQuantity)
          .findFirst()
          .orElse(0);
      reservedQuantities.merge(item.getProductId(), item.getQuantity(), Integer::sum);
      reservedQuantities.computeIfPresent(item.getProductId(), (k, v) -> Math.min(v, ordered));
    }
  }

  public List<OrderLine> getReservedItems() {
    List<OrderLine> reserved = new ArrayList<>();
    for (OrderLine item : items) {
      int reservedQuantity = reservedQuantities.getOrDefault(item.getProductId(), 0);
      if (reservedQuantity > 0) {
        reserved.add(new OrderLine(item.getProductId(), reservedQuantity, item.getUnitVolume()));
      }
    }
    return List.copyOf(reserved);
  }

  public List<OrderLine> getUnreservedItems() {
    List<OrderLine> missing = new ArrayList<>();
    for (OrderLine item : items) {
      int reservedQuantity = reservedQuantities.getOrDefault(item.getProductId(), 0);
      int remainingQuantity = Math.max(0, item.getQuantity() - reservedQuantity);
      if (remainingQuantity > 0) {
        missing.add(new OrderLine(item.getProductId(), remainingQuantity, item.getUnitVolume()));
      }
    }
    return List.copyOf(missing);
  }

  public boolean isFullyReserved() {
    return getUnreservedItems().isEmpty();
  }

  public void clearReservedItems() {
    reservedQuantities.clear();
  }

  @Override
  public String toString() {
    return "Order{id='" + id + "', customer='" + customerNodeId + "', microDepot='" + microDepotNodeId + "', demand=" + getDemandVolume() + ", creationTick=" + creationTick + ", priority=" + priorityLevel + ", status=" + status + ", vehicle='" + assignedVehicleId + "'}";
  }
}
