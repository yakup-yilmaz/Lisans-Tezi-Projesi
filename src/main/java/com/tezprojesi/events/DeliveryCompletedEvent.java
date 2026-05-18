package com.tezprojesi.events;

import java.util.Objects;

public final class DeliveryCompletedEvent implements Event {

  private final String orderId;
  private final String vehicleId;
  private final long createdTick;
  private final long completedTick;

  public DeliveryCompletedEvent(String orderId, String vehicleId, long createdTick, long completedTick) {
    this.orderId = Objects.requireNonNull(orderId, "orderId must not be null");
    this.vehicleId = Objects.requireNonNull(vehicleId, "vehicleId must not be null");
    if (createdTick < 0 || completedTick < createdTick) {
      throw new IllegalArgumentException("invalid tick values");
    }
    this.createdTick = createdTick;
    this.completedTick = completedTick;
  }

  public String getOrderId() {
    return orderId;
  }

  public String getVehicleId() {
    return vehicleId;
  }

  public long getCreatedTick() {
    return createdTick;
  }

  public long getCompletedTick() {
    return completedTick;
  }

  public long getDeliveryDurationTicks() {
    return completedTick - createdTick;
  }
}
