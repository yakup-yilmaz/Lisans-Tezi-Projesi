package com.tezprojesi.api.dto;

public class VehicleDto {
  private String id;
  private double capacityVolume;
  private double currentLoad;
  private String locationNodeId;
  private String status;
  private String fuelType;
  private double costPerKm;
  private java.util.Set<String> supportedCategories;
  private java.util.Set<String> capabilities;
  private String plateNumber;
  private String driverName;
  private double minimumDepartureLoadFraction;
  private int maxStopCount;
  private long maxWaitTicks;
  private double loadFraction;
  private double averageSpeedKmPerHour;
  private double loadingRateVolumePerTick;
  private double unloadingRateVolumePerTick;
  private long baseLoadTicks;
  private long baseUnloadTicks;
  private boolean dedicatedManual;

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public double getCapacityVolume() { return capacityVolume; }
  public void setCapacityVolume(double capacityVolume) { this.capacityVolume = capacityVolume; }
  public double getCurrentLoad() { return currentLoad; }
  public void setCurrentLoad(double currentLoad) { this.currentLoad = currentLoad; }
  public String getLocationNodeId() { return locationNodeId; }
  public void setLocationNodeId(String locationNodeId) { this.locationNodeId = locationNodeId; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public String getFuelType() { return fuelType; }
  public void setFuelType(String fuelType) { this.fuelType = fuelType; }
  public double getCostPerKm() { return costPerKm; }
  public void setCostPerKm(double costPerKm) { this.costPerKm = costPerKm; }
  public java.util.Set<String> getSupportedCategories() { return supportedCategories; }
  public void setSupportedCategories(java.util.Set<String> supportedCategories) { this.supportedCategories = supportedCategories; }
  public java.util.Set<String> getCapabilities() { return capabilities; }
  public void setCapabilities(java.util.Set<String> capabilities) { this.capabilities = capabilities; }
  public String getPlateNumber() { return plateNumber; }
  public void setPlateNumber(String plateNumber) { this.plateNumber = plateNumber; }
  public String getDriverName() { return driverName; }
  public void setDriverName(String driverName) { this.driverName = driverName; }
  public double getMinimumDepartureLoadFraction() { return minimumDepartureLoadFraction; }
  public void setMinimumDepartureLoadFraction(double minimumDepartureLoadFraction) { this.minimumDepartureLoadFraction = minimumDepartureLoadFraction; }
  public int getMaxStopCount() { return maxStopCount; }
  public void setMaxStopCount(int maxStopCount) { this.maxStopCount = maxStopCount; }
  public long getMaxWaitTicks() { return maxWaitTicks; }
  public void setMaxWaitTicks(long maxWaitTicks) { this.maxWaitTicks = maxWaitTicks; }
  public double getLoadFraction() { return loadFraction; }
  public void setLoadFraction(double loadFraction) { this.loadFraction = loadFraction; }
  public double getAverageSpeedKmPerHour() { return averageSpeedKmPerHour; }
  public void setAverageSpeedKmPerHour(double averageSpeedKmPerHour) { this.averageSpeedKmPerHour = averageSpeedKmPerHour; }
  public double getLoadingRateVolumePerTick() { return loadingRateVolumePerTick; }
  public void setLoadingRateVolumePerTick(double loadingRateVolumePerTick) { this.loadingRateVolumePerTick = loadingRateVolumePerTick; }
  public double getUnloadingRateVolumePerTick() { return unloadingRateVolumePerTick; }
  public void setUnloadingRateVolumePerTick(double unloadingRateVolumePerTick) { this.unloadingRateVolumePerTick = unloadingRateVolumePerTick; }
  public long getBaseLoadTicks() { return baseLoadTicks; }
  public void setBaseLoadTicks(long baseLoadTicks) { this.baseLoadTicks = baseLoadTicks; }
  public long getBaseUnloadTicks() { return baseUnloadTicks; }
  public void setBaseUnloadTicks(long baseUnloadTicks) { this.baseUnloadTicks = baseUnloadTicks; }
  public boolean isDedicatedManual() { return dedicatedManual; }
  public void setDedicatedManual(boolean dedicatedManual) { this.dedicatedManual = dedicatedManual; }
}
