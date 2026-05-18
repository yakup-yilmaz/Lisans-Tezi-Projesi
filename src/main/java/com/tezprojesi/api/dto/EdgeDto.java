package com.tezprojesi.api.dto;

public class EdgeDto {
  private String fromNodeId;
  private String toNodeId;
  private double distanceKm;
  private double trafficFactor;
  private double tollCost;
  private boolean trafficLocked;

  public String getFromNodeId() { return fromNodeId; }
  public void setFromNodeId(String fromNodeId) { this.fromNodeId = fromNodeId; }
  public String getToNodeId() { return toNodeId; }
  public void setToNodeId(String toNodeId) { this.toNodeId = toNodeId; }
  public double getDistanceKm() { return distanceKm; }
  public void setDistanceKm(double distanceKm) { this.distanceKm = distanceKm; }
  public double getTrafficFactor() { return trafficFactor; }
  public void setTrafficFactor(double trafficFactor) { this.trafficFactor = trafficFactor; }
  public double getTollCost() { return tollCost; }
  public void setTollCost(double tollCost) { this.tollCost = tollCost; }
  public boolean isTrafficLocked() { return trafficLocked; }
  public void setTrafficLocked(boolean trafficLocked) { this.trafficLocked = trafficLocked; }
}
