package com.tezprojesi.api.dto;

public class VehicleReportDto {
  private String vehicleId;
  private String fuelType;
  private String period;
  private long tripCount;
  private double totalDistanceKm;
  private double totalCost;
  private double totalVolume;
  private double averageLoadFraction;
  private long totalWaitTicks;
  private long totalLoadingTicks;
  private long totalUnloadingTicks;
  private long fromTick;
  private long toTick;
  private long multiStopTrips;
  private long singleStopTrips;
  private double averageNetScore;
  private double averagePlanBenefit;
  private double totalCargoValue;

  public String getVehicleId() { return vehicleId; }
  public void setVehicleId(String vehicleId) { this.vehicleId = vehicleId; }
  public String getFuelType() { return fuelType; }
  public void setFuelType(String fuelType) { this.fuelType = fuelType; }
  public String getPeriod() { return period; }
  public void setPeriod(String period) { this.period = period; }
  public long getTripCount() { return tripCount; }
  public void setTripCount(long tripCount) { this.tripCount = tripCount; }
  public double getTotalDistanceKm() { return totalDistanceKm; }
  public void setTotalDistanceKm(double totalDistanceKm) { this.totalDistanceKm = totalDistanceKm; }
  public double getTotalCost() { return totalCost; }
  public void setTotalCost(double totalCost) { this.totalCost = totalCost; }
  public double getTotalVolume() { return totalVolume; }
  public void setTotalVolume(double totalVolume) { this.totalVolume = totalVolume; }
  public double getAverageLoadFraction() { return averageLoadFraction; }
  public void setAverageLoadFraction(double averageLoadFraction) { this.averageLoadFraction = averageLoadFraction; }
  public long getTotalWaitTicks() { return totalWaitTicks; }
  public void setTotalWaitTicks(long totalWaitTicks) { this.totalWaitTicks = totalWaitTicks; }
  public long getTotalLoadingTicks() { return totalLoadingTicks; }
  public void setTotalLoadingTicks(long totalLoadingTicks) { this.totalLoadingTicks = totalLoadingTicks; }
  public long getTotalUnloadingTicks() { return totalUnloadingTicks; }
  public void setTotalUnloadingTicks(long totalUnloadingTicks) { this.totalUnloadingTicks = totalUnloadingTicks; }
  public long getFromTick() { return fromTick; }
  public void setFromTick(long fromTick) { this.fromTick = fromTick; }
  public long getToTick() { return toTick; }
  public void setToTick(long toTick) { this.toTick = toTick; }
  public long getMultiStopTrips() { return multiStopTrips; }
  public void setMultiStopTrips(long multiStopTrips) { this.multiStopTrips = multiStopTrips; }
  public long getSingleStopTrips() { return singleStopTrips; }
  public void setSingleStopTrips(long singleStopTrips) { this.singleStopTrips = singleStopTrips; }
  public double getAverageNetScore() { return averageNetScore; }
  public void setAverageNetScore(double averageNetScore) { this.averageNetScore = averageNetScore; }
  public double getAveragePlanBenefit() { return averagePlanBenefit; }
  public void setAveragePlanBenefit(double averagePlanBenefit) { this.averagePlanBenefit = averagePlanBenefit; }
  public double getTotalCargoValue() { return totalCargoValue; }
  public void setTotalCargoValue(double totalCargoValue) { this.totalCargoValue = totalCargoValue; }
}
