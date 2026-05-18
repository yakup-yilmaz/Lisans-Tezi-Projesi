package com.tezprojesi.api.dto;

import java.util.List;

public class VehicleDispatchReadinessDto {
  private String vehicleId;
  private String referenceId;
  private List<String> requestIds;
  private List<String> targetDepotIds;
  private double totalVolume;
  private double capacityVolume;
  private double loadFraction;
  private double minimumDepartureLoadFraction;
  private double immediateDepartureLoadFraction;
  private boolean minimumLoadReached;
  private long waitTicks;
  private long maxWaitTicks;
  private boolean maxWaitReached;
  private boolean urgent;
  private boolean readyToDispatch;
  private boolean awaitingApproval;
  /** True when %50–%84 bandinda onay bekleniyor (UI canli uyarisi). */
  private boolean capacityApprovalPending;
  private long firstRequestTick;
  private long lastRequestUpdateTick;
  private String routeSummary;
  private String cargoSummary;
  private String decisionReason;
  private String comparisonSummary;
  private int stopCount;
  private long missionTicks;
  private double deliveryDistanceKm;
  private double repositionDistanceKm;
  private double planCost;
  private double planBenefit;
  private double netScore;
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
  public List<String> getRequestIds() { return requestIds; }
  public void setRequestIds(List<String> requestIds) { this.requestIds = requestIds; }
  public List<String> getTargetDepotIds() { return targetDepotIds; }
  public void setTargetDepotIds(List<String> targetDepotIds) { this.targetDepotIds = targetDepotIds; }
  public double getTotalVolume() { return totalVolume; }
  public void setTotalVolume(double totalVolume) { this.totalVolume = totalVolume; }
  public double getCapacityVolume() { return capacityVolume; }
  public void setCapacityVolume(double capacityVolume) { this.capacityVolume = capacityVolume; }
  public double getLoadFraction() { return loadFraction; }
  public void setLoadFraction(double loadFraction) { this.loadFraction = loadFraction; }
  public double getMinimumDepartureLoadFraction() { return minimumDepartureLoadFraction; }
  public void setMinimumDepartureLoadFraction(double minimumDepartureLoadFraction) { this.minimumDepartureLoadFraction = minimumDepartureLoadFraction; }
  public double getImmediateDepartureLoadFraction() { return immediateDepartureLoadFraction; }
  public void setImmediateDepartureLoadFraction(double immediateDepartureLoadFraction) { this.immediateDepartureLoadFraction = immediateDepartureLoadFraction; }
  public boolean isMinimumLoadReached() { return minimumLoadReached; }
  public void setMinimumLoadReached(boolean minimumLoadReached) { this.minimumLoadReached = minimumLoadReached; }
  public long getWaitTicks() { return waitTicks; }
  public void setWaitTicks(long waitTicks) { this.waitTicks = waitTicks; }
  public long getMaxWaitTicks() { return maxWaitTicks; }
  public void setMaxWaitTicks(long maxWaitTicks) { this.maxWaitTicks = maxWaitTicks; }
  public boolean isMaxWaitReached() { return maxWaitReached; }
  public void setMaxWaitReached(boolean maxWaitReached) { this.maxWaitReached = maxWaitReached; }
  public boolean isUrgent() { return urgent; }
  public void setUrgent(boolean urgent) { this.urgent = urgent; }
  public boolean isReadyToDispatch() { return readyToDispatch; }
  public void setReadyToDispatch(boolean readyToDispatch) { this.readyToDispatch = readyToDispatch; }
  public boolean isAwaitingApproval() { return awaitingApproval; }
  public void setAwaitingApproval(boolean awaitingApproval) { this.awaitingApproval = awaitingApproval; }
  public boolean isCapacityApprovalPending() { return capacityApprovalPending; }
  public void setCapacityApprovalPending(boolean capacityApprovalPending) { this.capacityApprovalPending = capacityApprovalPending; }
  public long getFirstRequestTick() { return firstRequestTick; }
  public void setFirstRequestTick(long firstRequestTick) { this.firstRequestTick = firstRequestTick; }
  public long getLastRequestUpdateTick() { return lastRequestUpdateTick; }
  public void setLastRequestUpdateTick(long lastRequestUpdateTick) { this.lastRequestUpdateTick = lastRequestUpdateTick; }
  public String getRouteSummary() { return routeSummary; }
  public void setRouteSummary(String routeSummary) { this.routeSummary = routeSummary; }
  public String getCargoSummary() { return cargoSummary; }
  public void setCargoSummary(String cargoSummary) { this.cargoSummary = cargoSummary; }
  public String getDecisionReason() { return decisionReason; }
  public void setDecisionReason(String decisionReason) { this.decisionReason = decisionReason; }
  public String getComparisonSummary() { return comparisonSummary; }
  public void setComparisonSummary(String comparisonSummary) { this.comparisonSummary = comparisonSummary; }
  public int getStopCount() { return stopCount; }
  public void setStopCount(int stopCount) { this.stopCount = stopCount; }
  public long getMissionTicks() { return missionTicks; }
  public void setMissionTicks(long missionTicks) { this.missionTicks = missionTicks; }
  public double getDeliveryDistanceKm() { return deliveryDistanceKm; }
  public void setDeliveryDistanceKm(double deliveryDistanceKm) { this.deliveryDistanceKm = deliveryDistanceKm; }
  public double getRepositionDistanceKm() { return repositionDistanceKm; }
  public void setRepositionDistanceKm(double repositionDistanceKm) { this.repositionDistanceKm = repositionDistanceKm; }
  public double getPlanCost() { return planCost; }
  public void setPlanCost(double planCost) { this.planCost = planCost; }
  public double getPlanBenefit() { return planBenefit; }
  public void setPlanBenefit(double planBenefit) { this.planBenefit = planBenefit; }
  public double getNetScore() { return netScore; }
  public void setNetScore(double netScore) { this.netScore = netScore; }
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
