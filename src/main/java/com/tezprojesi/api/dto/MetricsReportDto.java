package com.tezprojesi.api.dto;

import java.util.Map;

public class MetricsReportDto {

  private long fromTick;
  private long toTick;
  private long totalOrders;
  private long completedOrders;
  private double totalDistance;
  private double avgDeliveryTime;
  private long delayedOrders;
  private Map<String, Double> distancePerVehicle;
  private Map<String, Long> utilizationPerVehicle;
  private Map<String, Integer> depotDispatchCount;
  private Map<String, Long> depotStockChange;

  public long getFromTick() {
    return fromTick;
  }

  public void setFromTick(long fromTick) {
    this.fromTick = fromTick;
  }

  public long getToTick() {
    return toTick;
  }

  public void setToTick(long toTick) {
    this.toTick = toTick;
  }

  public long getTotalOrders() {
    return totalOrders;
  }

  public void setTotalOrders(long totalOrders) {
    this.totalOrders = totalOrders;
  }

  public long getCompletedOrders() {
    return completedOrders;
  }

  public void setCompletedOrders(long completedOrders) {
    this.completedOrders = completedOrders;
  }

  public double getTotalDistance() {
    return totalDistance;
  }

  public void setTotalDistance(double totalDistance) {
    this.totalDistance = totalDistance;
  }

  public double getAvgDeliveryTime() {
    return avgDeliveryTime;
  }

  public void setAvgDeliveryTime(double avgDeliveryTime) {
    this.avgDeliveryTime = avgDeliveryTime;
  }

  public long getDelayedOrders() {
    return delayedOrders;
  }

  public void setDelayedOrders(long delayedOrders) {
    this.delayedOrders = delayedOrders;
  }

  public Map<String, Double> getDistancePerVehicle() {
    return distancePerVehicle;
  }

  public void setDistancePerVehicle(Map<String, Double> distancePerVehicle) {
    this.distancePerVehicle = distancePerVehicle;
  }

  public Map<String, Long> getUtilizationPerVehicle() {
    return utilizationPerVehicle;
  }

  public void setUtilizationPerVehicle(Map<String, Long> utilizationPerVehicle) {
    this.utilizationPerVehicle = utilizationPerVehicle;
  }

  public Map<String, Integer> getDepotDispatchCount() {
    return depotDispatchCount;
  }

  public void setDepotDispatchCount(Map<String, Integer> depotDispatchCount) {
    this.depotDispatchCount = depotDispatchCount;
  }

  public Map<String, Long> getDepotStockChange() {
    return depotStockChange;
  }

  public void setDepotStockChange(Map<String, Long> depotStockChange) {
    this.depotStockChange = depotStockChange;
  }
}
