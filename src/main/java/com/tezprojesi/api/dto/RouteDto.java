package com.tezprojesi.api.dto;

import java.util.List;

public class RouteDto {
  private String sourceNodeId;
  private String targetNodeId;
  private List<String> nodeIds;
  private double totalCost;
  private double totalDistanceKm;
  private double tollPenalty;

  public String getSourceNodeId() {
    return sourceNodeId;
  }

  public void setSourceNodeId(String sourceNodeId) {
    this.sourceNodeId = sourceNodeId;
  }

  public String getTargetNodeId() {
    return targetNodeId;
  }

  public void setTargetNodeId(String targetNodeId) {
    this.targetNodeId = targetNodeId;
  }

  public List<String> getNodeIds() {
    return nodeIds;
  }

  public void setNodeIds(List<String> nodeIds) {
    this.nodeIds = nodeIds;
  }

  public double getTotalCost() {
    return totalCost;
  }

  public void setTotalCost(double totalCost) {
    this.totalCost = totalCost;
  }

  public double getTotalDistanceKm() {
    return totalDistanceKm;
  }

  public void setTotalDistanceKm(double totalDistanceKm) {
    this.totalDistanceKm = totalDistanceKm;
  }

  public double getTollPenalty() {
    return tollPenalty;
  }

  public void setTollPenalty(double tollPenalty) {
    this.tollPenalty = tollPenalty;
  }
}
