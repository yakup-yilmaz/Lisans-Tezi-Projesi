package com.tezprojesi.api.dto;

import java.util.List;

public class VehicleReplenishmentGroupDto {
  private String vehicleId;
  private String vehicleStatus;
  private double capacityVolume;
  private double currentLoad;
  private double loadFraction;
  private String plateNumber;
  private String driverName;
  private String fuelType;
  private double costPerKm;
  private List<ReplenishmentDto> replenishments;
  private double totalVolume;
  private int replenishmentCount;

  public VehicleReplenishmentGroupDto() {}

  public VehicleReplenishmentGroupDto(
      String vehicleId,
      String vehicleStatus,
      double capacityVolume,
      double currentLoad,
      double loadFraction,
      String plateNumber,
      String driverName,
      String fuelType,
      double costPerKm,
      List<ReplenishmentDto> replenishments,
      double totalVolume,
      int replenishmentCount) {
    this.vehicleId = vehicleId;
    this.vehicleStatus = vehicleStatus;
    this.capacityVolume = capacityVolume;
    this.currentLoad = currentLoad;
    this.loadFraction = loadFraction;
    this.plateNumber = plateNumber;
    this.driverName = driverName;
    this.fuelType = fuelType;
    this.costPerKm = costPerKm;
    this.replenishments = replenishments;
    this.totalVolume = totalVolume;
    this.replenishmentCount = replenishmentCount;
  }

  public String getVehicleId() {
    return vehicleId;
  }

  public void setVehicleId(String vehicleId) {
    this.vehicleId = vehicleId;
  }

  public String getVehicleStatus() {
    return vehicleStatus;
  }

  public void setVehicleStatus(String vehicleStatus) {
    this.vehicleStatus = vehicleStatus;
  }

  public double getCapacityVolume() {
    return capacityVolume;
  }

  public void setCapacityVolume(double capacityVolume) {
    this.capacityVolume = capacityVolume;
  }

  public double getCurrentLoad() {
    return currentLoad;
  }

  public void setCurrentLoad(double currentLoad) {
    this.currentLoad = currentLoad;
  }

  public double getLoadFraction() {
    return loadFraction;
  }

  public void setLoadFraction(double loadFraction) {
    this.loadFraction = loadFraction;
  }

  public String getPlateNumber() {
    return plateNumber;
  }

  public void setPlateNumber(String plateNumber) {
    this.plateNumber = plateNumber;
  }

  public String getDriverName() {
    return driverName;
  }

  public void setDriverName(String driverName) {
    this.driverName = driverName;
  }

  public String getFuelType() {
    return fuelType;
  }

  public void setFuelType(String fuelType) {
    this.fuelType = fuelType;
  }

  public double getCostPerKm() {
    return costPerKm;
  }

  public void setCostPerKm(double costPerKm) {
    this.costPerKm = costPerKm;
  }

  public List<ReplenishmentDto> getReplenishments() {
    return replenishments;
  }

  public void setReplenishments(List<ReplenishmentDto> replenishments) {
    this.replenishments = replenishments;
  }

  public double getTotalVolume() {
    return totalVolume;
  }

  public void setTotalVolume(double totalVolume) {
    this.totalVolume = totalVolume;
  }

  public int getReplenishmentCount() {
    return replenishmentCount;
  }

  public void setReplenishmentCount(int replenishmentCount) {
    this.replenishmentCount = replenishmentCount;
  }
}
