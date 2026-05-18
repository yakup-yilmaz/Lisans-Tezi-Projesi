package com.tezprojesi.events;

import java.util.List;
import java.util.Objects;

public final class OrderAssignedEvent implements Event {
  private final String orderId;
  private final String depotId;
  private final String vehicleId;
  private final List<String> routeNodeIds;

  public OrderAssignedEvent(String orderId, String depotId, String vehicleId, List<String> routeNodeIds) {
    this.orderId = Objects.requireNonNull(orderId, "orderId must not be null");
    this.depotId = Objects.requireNonNull(depotId, "depotId must not be null");
    this.vehicleId = Objects.requireNonNull(vehicleId, "vehicleId must not be null");
    this.routeNodeIds = List.copyOf(Objects.requireNonNull(routeNodeIds, "routeNodeIds must not be null"));
  }

  public String getOrderId() {
    return orderId;
  }

  public String getDepotId() {
    return depotId;
  }

  public String getVehicleId() {
    return vehicleId;
  }

  public List<String> getRouteNodeIds() {
    return routeNodeIds;
  }
}
