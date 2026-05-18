package com.tezprojesi.api.dto;

public class TollUpdateRequest {
  private String fromNodeId;
  private String toNodeId;
  private double tollCost;
  private boolean bidirectional = true;

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

  public double getTollCost() {
    return tollCost;
  }

  public void setTollCost(double tollCost) {
    this.tollCost = tollCost;
  }

  public boolean isBidirectional() {
    return bidirectional;
  }

  public void setBidirectional(boolean bidirectional) {
    this.bidirectional = bidirectional;
  }
}
