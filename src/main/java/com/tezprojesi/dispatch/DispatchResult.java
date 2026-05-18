package com.tezprojesi.dispatch;

import java.util.List;
import java.util.Objects;

public final class DispatchResult {

  private final String orderId;
  private final String depotId;
  private final String vehicleId;
  private final List<String> routeNodeIds;
  private final double routeCost;

  public DispatchResult(String orderId, String depotId, String vehicleId, List<String> routeNodeIds, double routeCost) {
    this.orderId = Objects.requireNonNull(orderId, "orderId must not be null");
    this.depotId = Objects.requireNonNull(depotId, "depotId must not be null");
    this.vehicleId = Objects.requireNonNull(vehicleId, "vehicleId must not be null");
    this.routeNodeIds = List.copyOf(Objects.requireNonNull(routeNodeIds, "routeNodeIds must not be null"));
    if (routeCost < 0) {
      throw new IllegalArgumentException("routeCost must not be negative");
    }
    this.routeCost = routeCost;
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

  public double getRouteCost() {
    return routeCost;
  }
}
