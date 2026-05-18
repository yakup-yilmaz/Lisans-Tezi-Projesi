package com.tezprojesi.domain;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

public final class Vehicle {

  private final String id;
  private final double capacityVolume;
  private double currentLoad;
  private final String fuelType;
  private final double costPerKm;
  private final Set<ProductCategory> supportedCategories;
  private final Set<VehicleCapability> capabilities;
  private final String plateNumber;
  private final String driverName;
  private final double minimumDepartureLoadFraction;
  private final int maxStopCount;
  private final long maxWaitTicks;
  private final double averageSpeedKmPerHour;
  private final double loadingRateVolumePerTick;
  private final double unloadingRateVolumePerTick;
  private final long baseLoadTicks;
  private final long baseUnloadTicks;
  private String locationNodeId;
  private VehicleStatus status;
  private long waitingSinceTick;
  private final boolean dedicatedManual;
  private double currentMissionTotalCost;
  private double currentMissionCargoValue;
  private double currentMissionBcrRatio;

  public Vehicle(String id, double capacityVolume, String locationNodeId) {
    this(id, capacityVolume, locationNodeId, "DIESEL", 1.0,
        Set.of(ProductCategory.GENERAL, ProductCategory.FOOD, ProductCategory.ELECTRONICS, ProductCategory.MEDICAL, ProductCategory.CLEANING),
        Set.of(VehicleCapability.STANDARD_BOX), id + "-PLATE", "Unassigned", 0.5, 2, 180, 35.0, 10.0, 8.0, 3, 2, false);
  }

  public Vehicle(String id, double capacityVolume, String locationNodeId, String fuelType) {
    this(id, capacityVolume, locationNodeId, fuelType, 1.0,
        Set.of(ProductCategory.GENERAL, ProductCategory.FOOD, ProductCategory.ELECTRONICS, ProductCategory.MEDICAL, ProductCategory.CLEANING),
        Set.of(VehicleCapability.STANDARD_BOX), id + "-PLATE", "Unassigned", 0.5, 2, 180, 35.0, 10.0, 8.0, 3, 2, false);
  }

  public Vehicle(String id, double capacityVolume, String locationNodeId, String fuelType, double costPerKm, Set<ProductCategory> supportedCategories) {
    this(id, capacityVolume, locationNodeId, fuelType, costPerKm, supportedCategories, Set.of(VehicleCapability.STANDARD_BOX), id + "-PLATE", "Unassigned", 0.5, 2, 180, 35.0, 10.0, 8.0, 3, 2, false);
  }

  public Vehicle(
      String id,
      double capacityVolume,
      String locationNodeId,
      String fuelType,
      double costPerKm,
      Set<ProductCategory> supportedCategories,
      Set<VehicleCapability> capabilities,
      String plateNumber,
      String driverName,
      double minimumDepartureLoadFraction,
      int maxStopCount,
      long maxWaitTicks,
      double averageSpeedKmPerHour,
      double loadingRateVolumePerTick,
      double unloadingRateVolumePerTick,
      long baseLoadTicks,
      long baseUnloadTicks,
      boolean dedicatedManual) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    if (capacityVolume <= 0) {
      throw new IllegalArgumentException("capacityVolume must be positive");
    }
    if (costPerKm < 0) {
      throw new IllegalArgumentException("costPerKm cannot be negative");
    }
    if (minimumDepartureLoadFraction < 0 || minimumDepartureLoadFraction > 1) {
      throw new IllegalArgumentException("minimumDepartureLoadFraction must be in [0,1]");
    }
    if (maxStopCount <= 0) {
      throw new IllegalArgumentException("maxStopCount must be positive");
    }
    if (maxWaitTicks < 0) {
      throw new IllegalArgumentException("maxWaitTicks cannot be negative");
    }
    if (averageSpeedKmPerHour <= 0) {
      throw new IllegalArgumentException("averageSpeedKmPerHour must be positive");
    }
    if (loadingRateVolumePerTick <= 0 || unloadingRateVolumePerTick <= 0) {
      throw new IllegalArgumentException("loading/unloading rates must be positive");
    }
    if (baseLoadTicks < 0 || baseUnloadTicks < 0) {
      throw new IllegalArgumentException("base load/unload ticks cannot be negative");
    }
    this.capacityVolume = capacityVolume;
    this.currentLoad = 0.0;
    this.locationNodeId = Objects.requireNonNull(locationNodeId, "locationNodeId must not be null");
    this.fuelType = Objects.requireNonNull(fuelType, "fuelType must not be null");
    this.costPerKm = costPerKm;
    this.supportedCategories = Collections.unmodifiableSet(Set.copyOf(Objects.requireNonNull(supportedCategories, "supportedCategories")));
    this.capabilities = Collections.unmodifiableSet(Set.copyOf(Objects.requireNonNull(capabilities, "capabilities")));
    this.plateNumber = Objects.requireNonNull(plateNumber, "plateNumber");
    this.driverName = Objects.requireNonNull(driverName, "driverName");
    this.minimumDepartureLoadFraction = minimumDepartureLoadFraction;
    this.maxStopCount = maxStopCount;
    this.maxWaitTicks = maxWaitTicks;
    this.averageSpeedKmPerHour = averageSpeedKmPerHour;
    this.loadingRateVolumePerTick = loadingRateVolumePerTick;
    this.unloadingRateVolumePerTick = unloadingRateVolumePerTick;
    this.baseLoadTicks = baseLoadTicks;
    this.baseUnloadTicks = baseUnloadTicks;
    this.dedicatedManual = dedicatedManual;
    this.status = VehicleStatus.IDLE;
    this.waitingSinceTick = -1L;
  }

  /**
   * @deprecated Use the full 18-parameter constructor instead.
   */
  @Deprecated
  public Vehicle(
      String id,
      double capacityVolume,
      String locationNodeId,
      String fuelType,
      double costPerKm,
      Set<ProductCategory> supportedCategories,
      Set<VehicleCapability> capabilities,
      String plateNumber,
      String driverName,
      double minimumDepartureLoadFraction,
      int maxStopCount,
      long maxWaitTicks,
      double averageSpeedKmPerHour,
      double loadingRateVolumePerTick,
      double unloadingRateVolumePerTick,
      long baseLoadTicks,
      long baseUnloadTicks) {
    this(
        id,
        capacityVolume,
        locationNodeId,
        fuelType,
        costPerKm,
        supportedCategories,
        capabilities,
        plateNumber,
        driverName,
        minimumDepartureLoadFraction,
        maxStopCount,
        maxWaitTicks,
        averageSpeedKmPerHour,
        loadingRateVolumePerTick,
        unloadingRateVolumePerTick,
        baseLoadTicks,
        baseUnloadTicks,
        false);
  }

  /**
   * @deprecated Use the full 18-parameter constructor instead.
   */
  @Deprecated
  public Vehicle(
      String id,
      double capacityVolume,
      String locationNodeId,
      String fuelType,
      double costPerKm,
      Set<ProductCategory> supportedCategories,
      Set<VehicleCapability> capabilities,
      String plateNumber,
      String driverName,
      double minimumDepartureLoadFraction,
      int maxStopCount,
      long maxWaitTicks,
      double loadingRateVolumePerTick,
      double unloadingRateVolumePerTick,
      long baseLoadTicks,
      long baseUnloadTicks) {
    this(
        id,
        capacityVolume,
        locationNodeId,
        fuelType,
        costPerKm,
        supportedCategories,
        capabilities,
        plateNumber,
        driverName,
        minimumDepartureLoadFraction,
        maxStopCount,
        maxWaitTicks,
        35.0,
        loadingRateVolumePerTick,
        unloadingRateVolumePerTick,
        baseLoadTicks,
        baseUnloadTicks,
        false);
  }

  public String getId() {
    return id;
  }

  public double getCapacityVolume() {
    return capacityVolume;
  }

  public double getCurrentLoad() {
    return currentLoad;
  }

  public String getFuelType() {
    return fuelType;
  }

  public double getCostPerKm() {
    return costPerKm;
  }

  public Set<ProductCategory> getSupportedCategories() {
    return supportedCategories;
  }

  public Set<VehicleCapability> getCapabilities() {
    return capabilities;
  }

  public String getPlateNumber() {
    return plateNumber;
  }

  public String getDriverName() {
    return driverName;
  }

  public double getMinimumDepartureLoadFraction() {
    return minimumDepartureLoadFraction;
  }

  public int getMaxStopCount() {
    return maxStopCount;
  }

  public long getMaxWaitTicks() {
    return maxWaitTicks;
  }

  public double getAverageSpeedKmPerHour() {
    return averageSpeedKmPerHour;
  }

  public double getLoadingRateVolumePerTick() {
    return loadingRateVolumePerTick;
  }

  public double getUnloadingRateVolumePerTick() {
    return unloadingRateVolumePerTick;
  }

  public long getBaseLoadTicks() {
    return baseLoadTicks;
  }

  public long getBaseUnloadTicks() {
    return baseUnloadTicks;
  }

  public long getWaitingSinceTick() {
    return waitingSinceTick;
  }

  public void setWaitingSinceTick(long waitingSinceTick) {
    this.waitingSinceTick = waitingSinceTick;
  }

  public boolean isDedicatedManual() {
    return dedicatedManual;
  }

  public String getLocationNodeId() {
    return locationNodeId;
  }

  public void setLocationNodeId(String nodeId) {
    this.locationNodeId = Objects.requireNonNull(nodeId, "nodeId must not be null");
  }

  public VehicleStatus getStatus() {
    return status;
  }

  public boolean supportsAllCategories(Set<ProductCategory> categories) {
    return supportedCategories.containsAll(categories);
  }

  public boolean supportsCapabilities(Set<ProductCapability> requiredCapabilities) {
    long vMask = CapabilityMasks.vehicleCapabilityMask(capabilities);
    long req = CapabilityMasks.productCapabilityMask(requiredCapabilities);
    return CapabilityMasks.vehicleCoversRequiredMask(vMask, req);
  }

  public double getLoadFraction() {
    return capacityVolume <= 0 ? 0.0 : currentLoad / capacityVolume;
  }

  public boolean canTakeLoad(double volume) {
    if (volume < 0) {
      throw new IllegalArgumentException("volume must be non-negative");
    }
    return currentLoad + volume <= capacityVolume;
  }

  public void addLoad(double volume) {
    if (!canTakeLoad(volume)) {
      throw new IllegalStateException("load exceeds capacity");
    }
    currentLoad += volume;
  }

  public void removeLoad(double volume) {
    if (volume < 0) {
      throw new IllegalArgumentException("invalid unload volume");
    }
    double safeVolume = Math.min(volume, currentLoad);
    currentLoad = Math.max(0.0, currentLoad - safeVolume);
  }

  public void moveTo(String nodeId) {
    this.locationNodeId = Objects.requireNonNull(nodeId, "nodeId must not be null");
  }

  public void setStatus(VehicleStatus status) {
    this.status = Objects.requireNonNull(status, "status must not be null");
  }

  public void resetOperationalState(String locationNodeId) {
    this.locationNodeId = Objects.requireNonNull(locationNodeId, "locationNodeId must not be null");
    this.currentLoad = 0.0;
    this.status = VehicleStatus.IDLE;
    this.waitingSinceTick = -1L;
    this.currentMissionTotalCost = 0.0;
    this.currentMissionCargoValue = 0.0;
    this.currentMissionBcrRatio = 0.0;
  }

  public double getCurrentMissionTotalCost() {
    return currentMissionTotalCost;
  }

  public void setCurrentMissionTotalCost(double cost) {
    this.currentMissionTotalCost = cost;
  }

  public double getCurrentMissionCargoValue() {
    return currentMissionCargoValue;
  }

  public void setCurrentMissionCargoValue(double value) {
    this.currentMissionCargoValue = value;
  }

  public double getCurrentMissionBcrRatio() {
    return currentMissionBcrRatio;
  }

  public void setCurrentMissionBcrRatio(double ratio) {
    this.currentMissionBcrRatio = ratio;
  }

  @Override
  public String toString() {
    return "Vehicle{id='" + id + "', capacity=" + capacityVolume + ", currentLoad=" + currentLoad + ", location='" + locationNodeId + "', status=" + status + "}";
  }
}
