package com.tezprojesi.api.dto;

public class TrafficUpdateRequest {
  private String fromNodeId;
  private String toNodeId;
  private double trafficFactor;
  private boolean bidirectional = true;
  private boolean locked = true;

  public String getFromNodeId() {
    return fromNodeId;
  }

  public void setFromNodeId(String fromNodeId) {
    this.fromNodeId = fromNodeId;
  }

  public String getToNodeId() {
    return toNodeId;
  }

  public void setToNodeId(String toNodeId) {
    this.toNodeId = toNodeId;
  }

  public double getTrafficFactor() {
    return trafficFactor;
  }

  public void setTrafficFactor(double trafficFactor) {
    this.trafficFactor = trafficFactor;
  }

  public boolean isBidirectional() {
    return bidirectional;
  }

  public void setBidirectional(boolean bidirectional) {
    this.bidirectional = bidirectional;
  }

  public boolean isLocked() {
    return locked;
  }

  public void setLocked(boolean locked) {
    this.locked = locked;
  }
}
