package com.tezprojesi.api.controller;

import com.tezprojesi.api.dto.VehicleReportDto;
import com.tezprojesi.api.exception.NotFoundException;
import com.tezprojesi.api.repository.VehicleRepository;
import com.tezprojesi.simulation.SimulationEngine;
import com.tezprojesi.simulation.VehicleReportService;
import java.util.List;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/vehicle-reports")
public class VehicleReportController {
  private final VehicleRepository vehicleRepository;
  private final VehicleReportService vehicleReportService;
  private final SimulationEngine simulationEngine;

  public VehicleReportController(VehicleRepository vehicleRepository, VehicleReportService vehicleReportService, SimulationEngine simulationEngine) {
    this.vehicleRepository = Objects.requireNonNull(vehicleRepository);
    this.vehicleReportService = Objects.requireNonNull(vehicleReportService);
    this.simulationEngine = Objects.requireNonNull(simulationEngine);
  }

  @GetMapping
  public ResponseEntity<List<VehicleReportDto>> listReports(@RequestParam(defaultValue = "daily") String period) {
    long ticks = resolvePeriodTicks(period);
    return ResponseEntity.ok(vehicleRepository.findAll().stream()
        .map(vehicle -> toDto(vehicleReportService.reportForVehicle(vehicle, simulationEngine.getCurrentTick(), ticks), period))
        .toList());
  }

  @GetMapping("/{vehicleId}")
  public ResponseEntity<VehicleReportDto> getVehicleReport(@PathVariable String vehicleId, @RequestParam(defaultValue = "daily") String period) {
    long ticks = resolvePeriodTicks(period);
    var vehicle = vehicleRepository.findById(vehicleId).orElseThrow(() -> new NotFoundException("Vehicle not found"));
    return ResponseEntity.ok(toDto(vehicleReportService.reportForVehicle(vehicle, simulationEngine.getCurrentTick(), ticks), period));
  }

  private VehicleReportDto toDto(VehicleReportService.VehiclePeriodReport report, String period) {
    VehicleReportDto dto = new VehicleReportDto();
    dto.setVehicleId(report.vehicleId());
    dto.setFuelType(report.fuelType());
    dto.setPeriod(period);
    dto.setTripCount(report.tripCount());
    dto.setTotalDistanceKm(report.totalDistanceKm());
    dto.setTotalCost(report.totalCost());
    dto.setTotalVolume(report.totalVolume());
    dto.setAverageLoadFraction(report.averageLoadFraction());
    dto.setTotalWaitTicks(report.totalWaitTicks());
    dto.setTotalLoadingTicks(report.totalLoadingTicks());
    dto.setTotalUnloadingTicks(report.totalUnloadingTicks());
    dto.setFromTick(report.fromTick());
    dto.setToTick(report.toTick());
    dto.setMultiStopTrips(report.multiStopTrips());
    dto.setSingleStopTrips(report.singleStopTrips());
    dto.setAverageNetScore(report.averageNetScore());
    dto.setAveragePlanBenefit(report.averagePlanBenefit());
    dto.setTotalCargoValue(report.totalCargoValue());
    return dto;
  }

  private long resolvePeriodTicks(String period) {
    if ("weekly".equalsIgnoreCase(period)) return VehicleReportService.WEEKLY_TICKS;
    if ("monthly".equalsIgnoreCase(period)) return VehicleReportService.MONTHLY_TICKS;
    if ("yearly".equalsIgnoreCase(period)) return VehicleReportService.YEARLY_TICKS;
    return VehicleReportService.DAILY_TICKS;
  }
}
