package com.tezprojesi.domain;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class SimulationConfig {

  private final String mainDepotNodeId;
  private final List<String> microDepotNodeIds;
  private final int maxVehiclesPerRun;
  private final int maxTicks;

  public SimulationConfig(String mainDepotNodeId, List<String> microDepotNodeIds, int maxVehiclesPerRun, int maxTicks) {
    this.mainDepotNodeId = Objects.requireNonNull(mainDepotNodeId, "mainDepotNodeId must not be null");
    this.microDepotNodeIds = Collections.unmodifiableList(Objects.requireNonNull(microDepotNodeIds, "microDepotNodeIds must not be null"));
    if (microDepotNodeIds.isEmpty()) {
      throw new IllegalArgumentException("microDepotNodeIds must not be empty");
    }
    if (maxVehiclesPerRun <= 0) {
      throw new IllegalArgumentException("maxVehiclesPerRun must be positive");
    }
    if (maxTicks <= 0) {
      throw new IllegalArgumentException("maxTicks must be positive");
    }
    this.maxVehiclesPerRun = maxVehiclesPerRun;
    this.maxTicks = maxTicks;
  }

  public String getMainDepotNodeId() {
    return mainDepotNodeId;
  }

  public List<String> getMicroDepotNodeIds() {
    return microDepotNodeIds;
  }

  public int getMaxVehiclesPerRun() {
    return maxVehiclesPerRun;
  }

  public int getMaxTicks() {
    return maxTicks;
  }

  @Override
  public String toString() {
    return "SimulationConfig{mainDepot='" + mainDepotNodeId + "', microDepots=" + microDepotNodeIds + ", maxVehicles=" + maxVehiclesPerRun + ", maxTicks=" + maxTicks + "}";
  }
}
