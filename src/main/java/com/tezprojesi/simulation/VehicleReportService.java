package com.tezprojesi.simulation;

import com.tezprojesi.domain.Vehicle;
import java.util.ArrayList;
import java.util.List;

public final class VehicleReportService {
  public static final long DAILY_TICKS = 24L * 60L;
  public static final long WEEKLY_TICKS = 7L * DAILY_TICKS;
  public static final long MONTHLY_TICKS = 30L * DAILY_TICKS;
  public static final long YEARLY_TICKS = 365L * DAILY_TICKS;

  private final List<TripRecord> tripRecords = new ArrayList<>();

  public synchronized void recordTrip(
      String vehicleId,
      String fuelType, // Added for CO2 calculation
      long startTick,
      long endTick,
      double distanceKm,
      double cost,
      double volume,
      String routeType,
      double averageLoadFraction,
      long waitTicks,
      long loadingTicks,
      long unloadingTicks,
      int stopCount,
      double planBenefit,
      double netScore,
      // DETAILED COSTS for Thesis Analytics
      double fuelCost,
      double tollCost,
      double handlingCost,
      double timeCost,
      double delayPenalty,
      double stockoutPenalty,
      double totalCargoValue) {
    tripRecords.add(new TripRecord(
        vehicleId,
        fuelType,
        startTick,
        endTick,
        distanceKm,
        cost,
        volume,
        routeType,
        averageLoadFraction,
        waitTicks,
        loadingTicks,
        unloadingTicks,
        stopCount,
        planBenefit,
        netScore,
        fuelCost,
        tollCost,
        handlingCost,
        timeCost,
        delayPenalty,
        stockoutPenalty,
        totalCargoValue));
  }

  public synchronized List<TripRecord> getTripRecords() {
    return List.copyOf(tripRecords);
  }

  public synchronized void clear() {
    tripRecords.clear();
  }

  public synchronized VehiclePeriodReport reportForVehicle(Vehicle vehicle, long currentTick, long periodTicks) {
    long fromTick = Math.max(0, currentTick - periodTicks);
    List<TripRecord> relevant = tripRecords.stream()
        .filter(record -> record.vehicleId().equals(vehicle.getId()))
        .filter(record -> record.endTick() >= fromTick && record.endTick() <= currentTick)
        .toList();
    double distance = relevant.stream().mapToDouble(TripRecord::distanceKm).sum();
    double cost = relevant.stream().mapToDouble(TripRecord::cost).sum();
    double volume = relevant.stream().mapToDouble(TripRecord::volume).sum();
    double totalCargoValue = relevant.stream().mapToDouble(TripRecord::totalCargoValue).sum();
    double averageLoadFraction = relevant.isEmpty() ? 0.0 : relevant.stream().mapToDouble(TripRecord::averageLoadFraction).average().orElse(0.0);
    long totalWaitTicks = relevant.stream().mapToLong(TripRecord::waitTicks).sum();
    long totalLoadingTicks = relevant.stream().mapToLong(TripRecord::loadingTicks).sum();
    long totalUnloadingTicks = relevant.stream().mapToLong(TripRecord::unloadingTicks).sum();
    long multiStopTrips = relevant.stream().filter(record -> record.stopCount() > 1).count();
    long singleStopTrips = relevant.stream().filter(record -> record.stopCount() <= 1).count();
    // RETURN seferleri LES hesabina dahil edilmez (bos donus, karar kalitesi olcumu degil)
    List<TripRecord> scorableTrips = relevant.stream()
        .filter(r -> !"RETURN".equals(r.routeType())).toList();
    double averageNetScore = scorableTrips.isEmpty() ? 0.0 : scorableTrips.stream().mapToDouble(TripRecord::netScore).average().orElse(0.0);
    double averagePlanBenefit = scorableTrips.isEmpty() ? 0.0 : scorableTrips.stream().mapToDouble(TripRecord::planBenefit).average().orElse(0.0);
    long trips = relevant.size();
    return new VehiclePeriodReport(
        vehicle.getId(),
        vehicle.getFuelType(),
        trips,
        distance,
        cost,
        volume,
        averageLoadFraction,
        totalWaitTicks,
        totalLoadingTicks,
        totalUnloadingTicks,
        fromTick,
        currentTick,
        multiStopTrips,
        singleStopTrips,
        averageNetScore,
        averagePlanBenefit,
        totalCargoValue);
  }

  public record TripRecord(
      String vehicleId,
      String fuelType,
      long startTick,
      long endTick,
      double distanceKm,
      double cost,
      double volume,
      String routeType,
      double averageLoadFraction,
      long waitTicks,
      long loadingTicks,
      long unloadingTicks,
      int stopCount,
      double planBenefit,
      double netScore,
      double fuelCost,
      double tollCost,
      double handlingCost,
      double timeCost,
      double delayPenalty,
      double stockoutPenalty,
      double totalCargoValue) {}

  public record VehiclePeriodReport(
      String vehicleId,
      String fuelType,
      long tripCount,
      double totalDistanceKm,
      double totalCost,
      double totalVolume,
      double averageLoadFraction,
      long totalWaitTicks,
      long totalLoadingTicks,
      long totalUnloadingTicks,
      long fromTick,
      long toTick,
      long multiStopTrips,
      long singleStopTrips,
      double averageNetScore,
      double averagePlanBenefit,
      double totalCargoValue) {}
}
