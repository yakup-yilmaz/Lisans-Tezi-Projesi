package com.tezprojesi.domain;

import java.util.Objects;

public final class Edge {

  private final String fromNodeId;
  private final String toNodeId;
  private final double distanceKm;
  private final double trafficFactor;
  private final double tollCost;

  public Edge(String fromNodeId, String toNodeId, double distanceKm, double trafficFactor) {
    this(fromNodeId, toNodeId, distanceKm, trafficFactor, 0.0);
  }

  public Edge(String fromNodeId, String toNodeId, double distanceKm, double trafficFactor, double tollCost) {
    this.fromNodeId = Objects.requireNonNull(fromNodeId, "fromNodeId must not be null");
    this.toNodeId = Objects.requireNonNull(toNodeId, "toNodeId must not be null");
    if (distanceKm <= 0) {
      throw new IllegalArgumentException("distanceKm must be positive");
    }
    if (trafficFactor <= 0) {
      throw new IllegalArgumentException("trafficFactor must be positive");
    }
    if (tollCost < 0) {
      throw new IllegalArgumentException("tollCost cannot be negative");
    }
    this.distanceKm = distanceKm;
    this.trafficFactor = trafficFactor;
    this.tollCost = tollCost;
  }

  public String getFromNodeId() {
    return fromNodeId;
  }

  public String getToNodeId() {
    return toNodeId;
  }

  public double getDistanceKm() {
    return distanceKm;
  }

  public double getTrafficFactor() {
    return trafficFactor;
  }

  public double getTollCost() {
    return tollCost;
  }

  public double getCost() {
    return getEffectiveCost(1.0);
  }

  public double getEffectiveCost(double tollPenalty) {
    if (tollPenalty < 0) {
      throw new IllegalArgumentException("tollPenalty cannot be negative");
    }
    return distanceKm * trafficFactor + (tollCost * tollPenalty);
  }

  @Override
  public String toString() {
    return "Edge{from='" + fromNodeId + "', to='" + toNodeId + "', distance=" + distanceKm + ", trafficFactor=" + trafficFactor + "}";
  }
}
