package com.tezprojesi.api.dto;

public class SimulationSummaryDto {
  private double totalCost;
  private long totalOperationTicks;
  private double vehicleUtilizationPercentage;
  private double averageDeliveryTicks;
  private long totalTrips;
  private long multiStopTrips;
  private long singleStopTrips;
  private double multiStopAverageCost;
  private double singleStopAverageCost;
  private String comparisonSummary;
  
  // DETAILED METRICS for Jury Presentation
  private double totalFuelCost;
  private double totalTollCost;
  private double totalLaborCost;
  private double totalPenaltyCost;
  private double totalSavings;
  private double co2Emissions;
  private double co2Saved;
  private double totalCargoValue;

  public double getTotalCost() { return totalCost; }
  public void setTotalCost(double totalCost) { this.totalCost = totalCost; }
  public long getTotalOperationTicks() { return totalOperationTicks; }
  public void setTotalOperationTicks(long totalOperationTicks) { this.totalOperationTicks = totalOperationTicks; }
  public double getVehicleUtilizationPercentage() { return vehicleUtilizationPercentage; }
  public void setVehicleUtilizationPercentage(double vehicleUtilizationPercentage) { this.vehicleUtilizationPercentage = vehicleUtilizationPercentage; }
  public double getAverageDeliveryTicks() { return averageDeliveryTicks; }
  public void setAverageDeliveryTicks(double averageDeliveryTicks) { this.averageDeliveryTicks = averageDeliveryTicks; }
  public long getTotalTrips() { return totalTrips; }
  public void setTotalTrips(long totalTrips) { this.totalTrips = totalTrips; }
  public long getMultiStopTrips() { return multiStopTrips; }
  public void setMultiStopTrips(long multiStopTrips) { this.multiStopTrips = multiStopTrips; }
  public long getSingleStopTrips() { return singleStopTrips; }
  public void setSingleStopTrips(long singleStopTrips) { this.singleStopTrips = singleStopTrips; }
  public double getMultiStopAverageCost() { return multiStopAverageCost; }
  public void setMultiStopAverageCost(double multiStopAverageCost) { this.multiStopAverageCost = multiStopAverageCost; }
  public double getSingleStopAverageCost() { return singleStopAverageCost; }
  public void setSingleStopAverageCost(double singleStopAverageCost) { this.singleStopAverageCost = singleStopAverageCost; }
  public String getComparisonSummary() { return comparisonSummary; }
  public void setComparisonSummary(String comparisonSummary) { this.comparisonSummary = comparisonSummary; }

  public double getTotalFuelCost() { return totalFuelCost; }
  public void setTotalFuelCost(double totalFuelCost) { this.totalFuelCost = totalFuelCost; }
  public double getTotalTollCost() { return totalTollCost; }
  public void setTotalTollCost(double totalTollCost) { this.totalTollCost = totalTollCost; }
  public double getTotalLaborCost() { return totalLaborCost; }
  public void setTotalLaborCost(double totalLaborCost) { this.totalLaborCost = totalLaborCost; }
  public double getTotalPenaltyCost() { return totalPenaltyCost; }
  public void setTotalPenaltyCost(double totalPenaltyCost) { this.totalPenaltyCost = totalPenaltyCost; }
  public double getTotalSavings() { return totalSavings; }
  public void setTotalSavings(double totalSavings) { this.totalSavings = totalSavings; }
  public double getCo2Emissions() { return co2Emissions; }
  public void setCo2Emissions(double co2Emissions) { this.co2Emissions = co2Emissions; }
  public double getCo2Saved() { return co2Saved; }
  public void setCo2Saved(double co2Saved) { this.co2Saved = co2Saved; }
  public double getTotalCargoValue() { return totalCargoValue; }
  public void setTotalCargoValue(double totalCargoValue) { this.totalCargoValue = totalCargoValue; }
}
