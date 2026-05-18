package com.tezprojesi.api.dto;

import java.time.Instant;
import java.util.List;

public class ReplenishmentDto {
  private String id;
  private String sourceDepotId;
  private String targetDepotId;
  private String status;
  private String assignedVehicleId;
  private double totalVolume;
  private Instant createdAt;
  private List<OrderItemDto> items;
  private long createdTick;
  private String decisionReason;
  private String comparisonSummary;
  private String routeSummary;
  private String cargoSummary;
  private double planBenefit;
  private double netScore;
  private double planCost;
  private double totalFinancialValue;
  private boolean awaitingApproval;

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getSourceDepotId() { return sourceDepotId; }
  public void setSourceDepotId(String sourceDepotId) { this.sourceDepotId = sourceDepotId; }
  public String getTargetDepotId() { return targetDepotId; }
  public void setTargetDepotId(String targetDepotId) { this.targetDepotId = targetDepotId; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public String getAssignedVehicleId() { return assignedVehicleId; }
  public void setAssignedVehicleId(String assignedVehicleId) { this.assignedVehicleId = assignedVehicleId; }
  public double getTotalVolume() { return totalVolume; }
  public void setTotalVolume(double totalVolume) { this.totalVolume = totalVolume; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public List<OrderItemDto> getItems() { return items; }
  public void setItems(List<OrderItemDto> items) { this.items = items; }
  public long getCreatedTick() { return createdTick; }
  public void setCreatedTick(long createdTick) { this.createdTick = createdTick; }
  public String getDecisionReason() { return decisionReason; }
  public void setDecisionReason(String decisionReason) { this.decisionReason = decisionReason; }
  public String getComparisonSummary() { return comparisonSummary; }
  public void setComparisonSummary(String comparisonSummary) { this.comparisonSummary = comparisonSummary; }
  public String getRouteSummary() { return routeSummary; }
  public void setRouteSummary(String routeSummary) { this.routeSummary = routeSummary; }
  public String getCargoSummary() { return cargoSummary; }
  public void setCargoSummary(String cargoSummary) { this.cargoSummary = cargoSummary; }
  public double getPlanBenefit() { return planBenefit; }
  public void setPlanBenefit(double planBenefit) { this.planBenefit = planBenefit; }
  public double getNetScore() { return netScore; }
  public void setNetScore(double netScore) { this.netScore = netScore; }
  public double getPlanCost() { return planCost; }
  public void setPlanCost(double planCost) { this.planCost = planCost; }
  public double getTotalFinancialValue() { return totalFinancialValue; }
  public void setTotalFinancialValue(double totalFinancialValue) { this.totalFinancialValue = totalFinancialValue; }
  public boolean isAwaitingApproval() { return awaitingApproval; }
  public void setAwaitingApproval(boolean awaitingApproval) { this.awaitingApproval = awaitingApproval; }
}
