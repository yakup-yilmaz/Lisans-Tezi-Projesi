package com.tezprojesi.domain;

public final class SimulationMetrics {

  private double totalDistanceKm;
  private double totalCost;
  private double totalDelaySeconds;
  private int completedDeliveries;
  private double totalVehicleUtilization;
  private int vehicleUtilizationSamples;

  public SimulationMetrics() {
    this.totalDistanceKm = 0.0;
    this.totalCost = 0.0;
    this.totalDelaySeconds = 0.0;
    this.completedDeliveries = 0;
    this.totalVehicleUtilization = 0.0;
    this.vehicleUtilizationSamples = 0;
  }

  public void recordDistance(double distanceKm) {
    if (distanceKm < 0) {
      throw new IllegalArgumentException("distanceKm must not be negative");
    }
    this.totalDistanceKm += distanceKm;
  }

  public void recordCost(double cost) {
    if (cost < 0) {
      throw new IllegalArgumentException("cost must not be negative");
    }
    this.totalCost += cost;
  }

  public void recordDelay(double delaySeconds) {
    if (delaySeconds < 0) {
      throw new IllegalArgumentException("delaySeconds must not be negative");
    }
    this.totalDelaySeconds += delaySeconds;
  }

  public void recordCompletedDelivery() {
    this.completedDeliveries++;
  }

  public void recordVehicleUtilization(double utilizationFraction) {
    if (utilizationFraction < 0 || utilizationFraction > 1) {
      throw new IllegalArgumentException("utilization must be between 0 and 1");
    }
    this.totalVehicleUtilization += utilizationFraction;
    this.vehicleUtilizationSamples++;
  }

  public double getTotalDistanceKm() {
    return totalDistanceKm;
  }

  public double getTotalCost() {
    return totalCost;
  }

  public double getAverageDelaySeconds() {
    return completedDeliveries > 0 ? totalDelaySeconds / completedDeliveries : 0.0;
  }

  public int getCompletedDeliveries() {
    return completedDeliveries;
  }

  public double getAverageVehicleUtilization() {
    return vehicleUtilizationSamples > 0 ? totalVehicleUtilization / vehicleUtilizationSamples : 0.0;
  }

  @Override
  public String toString() {
    return "SimulationMetrics{totalDistance=" + totalDistanceKm + ", totalCost=" + totalCost + ", averageDelay=" + getAverageDelaySeconds() + ", completedDeliveries=" + completedDeliveries + ", avgVehicleUtilization=" + getAverageVehicleUtilization() + "}";
  }
}
