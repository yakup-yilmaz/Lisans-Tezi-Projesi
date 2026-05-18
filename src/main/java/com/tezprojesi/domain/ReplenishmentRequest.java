package com.tezprojesi.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ReplenishmentRequest {
  private final String id;
  private final String sourceDepotId;
  private final String targetDepotId;
  private List<OrderLine> items;
  private final Instant createdAt;
  private final long createdTick;
  private long lastUpdatedTick;
  private ReplenishmentStatus status;
  private String assignedVehicleId;
  private String decisionReason;
  private String comparisonSummary;
  private String routeSummary;
  private String cargoSummary;
  private double planBenefit;
  private double netScore;
  private double planCost;
  private double totalFinancialValue;
  private boolean manual;

  public ReplenishmentRequest(String id, String sourceDepotId, String targetDepotId, List<OrderLine> items) {
    this(id, sourceDepotId, targetDepotId, items, 0L);
  }

  public ReplenishmentRequest(String id, String sourceDepotId, String targetDepotId, List<OrderLine> items, long createdTick) {
    this.id = Objects.requireNonNull(id, "id");
    this.sourceDepotId = Objects.requireNonNull(sourceDepotId, "sourceDepotId");
    this.targetDepotId = Objects.requireNonNull(targetDepotId, "targetDepotId");
    this.items = List.copyOf(Objects.requireNonNull(items, "items"));
    if (items.isEmpty()) {
      throw new IllegalArgumentException("items must not be empty");
    }
    this.createdAt = Instant.now();
    this.createdTick = Math.max(0L, createdTick);
    this.lastUpdatedTick = this.createdTick;
    this.status = ReplenishmentStatus.CREATED;
  }

  public String getId() {
    return id;
  }

  public String getSourceDepotId() {
    return sourceDepotId;
  }

  public String getTargetDepotId() {
    return targetDepotId;
  }

  public List<OrderLine> getItems() {
    return items;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public long getCreatedTick() {
    return createdTick;
  }

  public long getLastUpdatedTick() {
    return lastUpdatedTick;
  }

  public ReplenishmentStatus getStatus() {
    return status;
  }

  public String getAssignedVehicleId() {
    return assignedVehicleId;
  }

  public String getDecisionReason() {
    return decisionReason;
  }

  public String getComparisonSummary() {
    return comparisonSummary;
  }

  public String getRouteSummary() {
    return routeSummary;
  }

  public String getCargoSummary() {
    return cargoSummary;
  }

  public double getPlanBenefit() {
    return planBenefit;
  }

  public double getNetScore() {
    return netScore;
  }

  public double getPlanCost() {
    return planCost;
  }

  public boolean isManual() {
    return manual;
  }

  public void setManual(boolean manual) {
    this.manual = manual;
  }

  public double getTotalFinancialValue() {
    return totalFinancialValue;
  }

  public void setTotalFinancialValue(double totalFinancialValue) {
    this.totalFinancialValue = totalFinancialValue;
  }

  public double getTotalVolume() {
    return items.stream().mapToDouble(OrderLine::getLineVolume).sum();
  }

  public void mergeItems(List<OrderLine> additionalItems, long mergedAtTick) {
    if (this.status != ReplenishmentStatus.CREATED) {
      throw new IllegalStateException("Cannot merge items into request in status: " + this.status);
    }
    Map<String, OrderLine> merged = new LinkedHashMap<>();
    for (OrderLine item : items) {
      merged.put(item.getProductId(), item);
    }
    for (OrderLine item : additionalItems) {
      OrderLine current = merged.get(item.getProductId());
      if (current == null) {
        merged.put(item.getProductId(), item);
        continue;
      }
      merged.put(item.getProductId(), new OrderLine(
          item.getProductId(),
          current.getQuantity() + item.getQuantity(),
          current.getUnitVolume()));
    }
    this.items = List.copyOf(new ArrayList<>(merged.values()));
    this.lastUpdatedTick = Math.max(this.lastUpdatedTick, mergedAtTick);
  }

  public void assignVehicle(String vehicleId) {
    String normalizedVehicleId = Objects.requireNonNull(vehicleId, "vehicleId");
    if (this.status == ReplenishmentStatus.COMPLETED) {
      throw new IllegalStateException("Cannot assign vehicle to completed request: " + id);
    }
    if (this.status == ReplenishmentStatus.IN_TRANSIT) {
      if (!normalizedVehicleId.equals(this.assignedVehicleId)) {
        throw new IllegalStateException(
            "Cannot reassign in-transit request " + id
                + " from " + assignedVehicleId
                + " to " + normalizedVehicleId);
      }
      return;
    }
    if (assignedVehicleId != null && !assignedVehicleId.equals(normalizedVehicleId)) {
      throw new IllegalStateException(
          "Request " + id + " already assigned to " + assignedVehicleId);
    }
    this.assignedVehicleId = normalizedVehicleId;
    this.status = ReplenishmentStatus.ASSIGNED;
  }

  public void resetToCreated() {
    if (this.status == ReplenishmentStatus.COMPLETED || this.status == ReplenishmentStatus.IN_TRANSIT) {
      return;
    }
    this.status = ReplenishmentStatus.CREATED;
    this.assignedVehicleId = null;
  }

  public void annotateDecision(String decisionReason, String routeSummary, String cargoSummary) {
    annotateDecision(decisionReason, routeSummary, cargoSummary, null, 0.0, 0.0);
  }

  public void annotateDecision(
      String decisionReason,
      String routeSummary,
      String cargoSummary,
      String comparisonSummary,
      double planBenefit,
      double netScore) {
    this.decisionReason = decisionReason;
    this.routeSummary = routeSummary;
    this.cargoSummary = cargoSummary;
    this.comparisonSummary = comparisonSummary;
    this.planBenefit = planBenefit;
    this.netScore = netScore;
    this.planCost = planBenefit - netScore;
  }

  public void markInTransit() {
    if (assignedVehicleId == null) {
      throw new IllegalStateException("Cannot mark replenishment in transit before assigning a vehicle");
    }
    this.status = ReplenishmentStatus.IN_TRANSIT;
  }

  /**
   * Ana depo stogu baska bir arac tarafindan tuketildiginde veya dispatch iptal edildiginde,
   * reposition/onay oncesi ASSIGNED talepleri tekrar planlanabilir CREATED durumuna alinir.
   */
  public void revertAssignedToCreated(String vehicleId) {
    if (this.status != ReplenishmentStatus.ASSIGNED) {
      return;
    }
    if (vehicleId == null || this.assignedVehicleId == null || !this.assignedVehicleId.equals(vehicleId)) {
      return;
    }
    this.assignedVehicleId = null;
    this.status = ReplenishmentStatus.CREATED;
  }

  public void complete() {
    if (this.status != ReplenishmentStatus.IN_TRANSIT) {
      throw new IllegalStateException("Cannot complete request in status: " + this.status);
    }
    this.status = ReplenishmentStatus.COMPLETED;
  }
}
