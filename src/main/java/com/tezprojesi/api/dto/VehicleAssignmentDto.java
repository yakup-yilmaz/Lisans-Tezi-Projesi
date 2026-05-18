package com.tezprojesi.api.dto;

import java.util.List;

public class VehicleAssignmentDto {
  private String vehicleId;
  private String referenceId;
  private String routeType;
  private List<String> plannedNodeIds;
  private List<String> remainingNodeIds;
  private String activeDepotId;
  private int remainingStopCount;
  private double loadFraction;
  private double totalVolume;
  private double capacityVolume;
  private double distanceKm;
  private long waitTicks;
  private long totalLoadingTicks;
  private long totalUnloadingTicks;
  private long loadingRemainingTicks;
  private long unloadingRemainingTicks;
  private String currentFromNodeId;
  private String currentToNodeId;
  private long segmentRemainingTicks;
  private long segmentTotalTicks;
  private long remainingTotalTicks;
  private long startedAtTick;
  private long expectedCompletionTick;
  private String statusDetail;
  private String decisionReason;
  private String departureReason;
  private String routeSummary;
  private String cargoSummary;
  private String comparisonSummary;
  private double planCost;
  private double planBenefit;
  private double netScore;
  private int plannedStopCount;
  private long missionTicks;
  private double deliveryDistanceKm;
  private double repositionDistanceKm;
  private double routeCost;
  private double handlingCost;
  private double timeCost;
  private double extraStopCost;
  private double delayPenalty;
  private double stockoutPenalty;
  private double opportunityCost;

  public String getVehicleId() { return vehicleId; }
  public void setVehicleId(String vehicleId) { this.vehicleId = vehicleId; }
  public String getReferenceId() { return referenceId; }
  public void setReferenceId(String referenceId) { this.referenceId = referenceId; }
  public String getRouteType() { return routeType; }
  public void setRouteType(String routeType) { this.routeType = routeType; }
  public List<String> getPlannedNodeIds() { return plannedNodeIds; }
  public void setPlannedNodeIds(List<String> plannedNodeIds) { this.plannedNodeIds = plannedNodeIds; }
  public List<String> getRemainingNodeIds() { return remainingNodeIds; }
  public void setRemainingNodeIds(List<String> remainingNodeIds) { this.remainingNodeIds = remainingNodeIds; }
  public String getActiveDepotId() { return activeDepotId; }
  public void setActiveDepotId(String activeDepotId) { this.activeDepotId = activeDepotId; }
  public int getRemainingStopCount() { return remainingStopCount; }
  public void setRemainingStopCount(int remainingStopCount) { this.remainingStopCount = remainingStopCount; }
  public double getLoadFraction() { return loadFraction; }
  public void setLoadFraction(double loadFraction) { this.loadFraction = loadFraction; }
  public double getTotalVolume() { return totalVolume; }
  public void setTotalVolume(double totalVolume) { this.totalVolume = totalVolume; }
  public double getCapacityVolume() { return capacityVolume; }
  public void setCapacityVolume(double capacityVolume) { this.capacityVolume = capacityVolume; }
  public double getDistanceKm() { return distanceKm; }
  public void setDistanceKm(double distanceKm) { this.distanceKm = distanceKm; }
  public long getWaitTicks() { return waitTicks; }
  public void setWaitTicks(long waitTicks) { this.waitTicks = waitTicks; }
  public long getTotalLoadingTicks() { return totalLoadingTicks; }
  public void setTotalLoadingTicks(long totalLoadingTicks) { this.totalLoadingTicks = totalLoadingTicks; }
  public long getTotalUnloadingTicks() { return totalUnloadingTicks; }
  public void setTotalUnloadingTicks(long totalUnloadingTicks) { this.totalUnloadingTicks = totalUnloadingTicks; }
  public long getLoadingRemainingTicks() { return loadingRemainingTicks; }
  public void setLoadingRemainingTicks(long loadingRemainingTicks) { this.loadingRemainingTicks = loadingRemainingTicks; }
  public long getUnloadingRemainingTicks() { return unloadingRemainingTicks; }
  public void setUnloadingRemainingTicks(long unloadingRemainingTicks) { this.unloadingRemainingTicks = unloadingRemainingTicks; }
  public String getCurrentFromNodeId() { return currentFromNodeId; }
  public void setCurrentFromNodeId(String currentFromNodeId) { this.currentFromNodeId = currentFromNodeId; }
  public String getCurrentToNodeId() { return currentToNodeId; }
  public void setCurrentToNodeId(String currentToNodeId) { this.currentToNodeId = currentToNodeId; }
  public long getSegmentRemainingTicks() { return segmentRemainingTicks; }
  public void setSegmentRemainingTicks(long segmentRemainingTicks) { this.segmentRemainingTicks = segmentRemainingTicks; }
  public long getSegmentTotalTicks() { return segmentTotalTicks; }
  public void setSegmentTotalTicks(long segmentTotalTicks) { this.segmentTotalTicks = segmentTotalTicks; }
  public long getRemainingTotalTicks() { return remainingTotalTicks; }
  public void setRemainingTotalTicks(long remainingTotalTicks) { this.remainingTotalTicks = remainingTotalTicks; }
  public long getStartedAtTick() { return startedAtTick; }
  public void setStartedAtTick(long startedAtTick) { this.startedAtTick = startedAtTick; }
  public long getExpectedCompletionTick() { return expectedCompletionTick; }
  public void setExpectedCompletionTick(long expectedCompletionTick) { this.expectedCompletionTick = expectedCompletionTick; }
  public String getStatusDetail() { return statusDetail; }
  public void setStatusDetail(String statusDetail) { this.statusDetail = statusDetail; }
  public String getDecisionReason() { return decisionReason; }
  public void setDecisionReason(String decisionReason) { this.decisionReason = decisionReason; }
  public String getDepartureReason() { return departureReason; }
  public void setDepartureReason(String departureReason) { this.departureReason = departureReason; }
  public String getRouteSummary() { return routeSummary; }
  public void setRouteSummary(String routeSummary) { this.routeSummary = routeSummary; }
  public String getCargoSummary() { return cargoSummary; }
  public void setCargoSummary(String cargoSummary) { this.cargoSummary = cargoSummary; }
  public String getComparisonSummary() { return comparisonSummary; }
  public void setComparisonSummary(String comparisonSummary) { this.comparisonSummary = comparisonSummary; }
  public double getPlanCost() { return planCost; }
  public void setPlanCost(double planCost) { this.planCost = planCost; }
  public double getPlanBenefit() { return planBenefit; }
  public void setPlanBenefit(double planBenefit) { this.planBenefit = planBenefit; }
  public double getNetScore() { return netScore; }
  public void setNetScore(double netScore) { this.netScore = netScore; }
  public int getPlannedStopCount() { return plannedStopCount; }
  public void setPlannedStopCount(int plannedStopCount) { this.plannedStopCount = plannedStopCount; }
  public long getMissionTicks() { return missionTicks; }
  public void setMissionTicks(long missionTicks) { this.missionTicks = missionTicks; }
  public double getDeliveryDistanceKm() { return deliveryDistanceKm; }
  public void setDeliveryDistanceKm(double deliveryDistanceKm) { this.deliveryDistanceKm = deliveryDistanceKm; }
  public double getRepositionDistanceKm() { return repositionDistanceKm; }
  public void setRepositionDistanceKm(double repositionDistanceKm) { this.repositionDistanceKm = repositionDistanceKm; }
  public double getRouteCost() { return routeCost; }
  public void setRouteCost(double routeCost) { this.routeCost = routeCost; }
  public double getHandlingCost() { return handlingCost; }
  public void setHandlingCost(double handlingCost) { this.handlingCost = handlingCost; }
  public double getTimeCost() { return timeCost; }
  public void setTimeCost(double timeCost) { this.timeCost = timeCost; }
  public double getExtraStopCost() { return extraStopCost; }
  public void setExtraStopCost(double extraStopCost) { this.extraStopCost = extraStopCost; }
  public double getDelayPenalty() { return delayPenalty; }
  public void setDelayPenalty(double delayPenalty) { this.delayPenalty = delayPenalty; }
  public double getStockoutPenalty() { return stockoutPenalty; }
  public void setStockoutPenalty(double stockoutPenalty) { this.stockoutPenalty = stockoutPenalty; }
  public double getOpportunityCost() { return opportunityCost; }
  public void setOpportunityCost(double opportunityCost) { this.opportunityCost = opportunityCost; }
}
