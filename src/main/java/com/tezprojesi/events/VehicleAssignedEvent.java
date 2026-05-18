package com.tezprojesi.events;

import java.util.Objects;

public final class VehicleAssignedEvent implements Event {
  private final String vehicleId;
  private final String orderId;
  private final String depotId;

  public VehicleAssignedEvent(String vehicleId, String orderId, String depotId) {
    this.vehicleId = Objects.requireNonNull(vehicleId, "vehicleId must not be null");
    this.orderId = Objects.requireNonNull(orderId, "orderId must not be null");
    this.depotId = Objects.requireNonNull(depotId, "depotId must not be null");
  }

  public String getVehicleId() {
    return vehicleId;
  }

  public String getOrderId() {
    return orderId;
  }

  public String getDepotId() {
    return depotId;
  }
}
