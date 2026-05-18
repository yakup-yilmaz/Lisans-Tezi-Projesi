package com.tezprojesi.api.dto;

import java.util.Map;

public class KpiDto {
  private long totalOrders;
  private long completedOrders;
  private double totalDistance;
  private double avgDeliveryTime;
  private double vehicleUtilization;
  private Map<String, Double> depotStock;

  public long getTotalOrders() { return totalOrders; }
  public void setTotalOrders(long totalOrders) { this.totalOrders = totalOrders; }
  public long getCompletedOrders() { return completedOrders; }
  public void setCompletedOrders(long completedOrders) { this.completedOrders = completedOrders; }
  public double getTotalDistance() { return totalDistance; }
  public void setTotalDistance(double totalDistance) { this.totalDistance = totalDistance; }
  public double getAvgDeliveryTime() { return avgDeliveryTime; }
  public void setAvgDeliveryTime(double avgDeliveryTime) { this.avgDeliveryTime = avgDeliveryTime; }
  public double getVehicleUtilization() { return vehicleUtilization; }
  public void setVehicleUtilization(double vehicleUtilization) { this.vehicleUtilization = vehicleUtilization; }
  public Map<String, Double> getDepotStock() { return depotStock; }
  public void setDepotStock(Map<String, Double> depotStock) { this.depotStock = depotStock; }
}
