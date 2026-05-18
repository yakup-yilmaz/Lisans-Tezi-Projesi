package com.tezprojesi.api.controller;

import com.tezprojesi.api.dto.KpiDto;
import com.tezprojesi.api.dto.MetricsReportDto;
import com.tezprojesi.simulation.MetricsEventHandler;
import com.tezprojesi.simulation.SimulationEngine;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/metrics")
public class MetricsController {

  private final MetricsEventHandler metricsEventHandler;
  private final SimulationEngine simulationEngine;

  public MetricsController(MetricsEventHandler metricsEventHandler, SimulationEngine simulationEngine) {
    this.metricsEventHandler = Objects.requireNonNull(metricsEventHandler);
    this.simulationEngine = Objects.requireNonNull(simulationEngine);
  }

  @GetMapping("/kpis")
  public ResponseEntity<KpiDto> getKpis() {
    KpiDto dto = new KpiDto();
    dto.setTotalOrders(metricsEventHandler.getTotalOrders());
    dto.setCompletedOrders(metricsEventHandler.getCompletedOrders());
    dto.setTotalDistance(metricsEventHandler.getTotalDistance());
    dto.setAvgDeliveryTime(metricsEventHandler.getAverageDeliveryTicks());
    dto.setVehicleUtilization(computeVehicleUtilizationPercentage());
    dto.setDepotStock(simulationEngine.getDepotStock());
    return ResponseEntity.ok(dto);
  }

  @GetMapping("/distance-per-vehicle")
  public ResponseEntity<java.util.Map<String, Double>> getDistancePerVehicle() {
    return ResponseEntity.ok(metricsEventHandler.getDistancePerVehicle());
  }

  @GetMapping("/utilization-per-vehicle")
  public ResponseEntity<java.util.Map<String, Long>> getUtilizationPerVehicle() {
    return ResponseEntity.ok(metricsEventHandler.getUtilizationPerVehicle());
  }

  @GetMapping("/depot-dispatch-count")
  public ResponseEntity<java.util.Map<String, Integer>> getDepotDispatchCount() {
    return ResponseEntity.ok(metricsEventHandler.getDepotDispatchCount());
  }

  @GetMapping("/delayed-orders")
  public ResponseEntity<Integer> getDelayedOrders() {
    return ResponseEntity.ok(metricsEventHandler.getDelayedOrders());
  }

  @GetMapping("/depot-stock-changes")
  public ResponseEntity<java.util.Map<String, Long>> getDepotStockChanges() {
    return ResponseEntity.ok(metricsEventHandler.getDepotStockChange());
  }

  @GetMapping("/report")
  public ResponseEntity<MetricsReportDto> getMetricsReport(
      @RequestParam(defaultValue = "1") long from,
      @RequestParam(defaultValue = "0") long to) {
    if (from < 0 || to < 0 || (to > 0 && to < from)) {
      return ResponseEntity.badRequest().build();
    }

    MetricsReportDto report = new MetricsReportDto();
    report.setFromTick(from);
    report.setToTick(to > 0 ? to : simulationEngine.getCurrentTick());
    report.setTotalOrders(metricsEventHandler.getTotalOrders());
    report.setCompletedOrders(metricsEventHandler.getCompletedOrders());
    report.setTotalDistance(metricsEventHandler.getTotalDistance());
    report.setAvgDeliveryTime(metricsEventHandler.getAverageDeliveryTicks());
    report.setDelayedOrders(metricsEventHandler.getDelayedOrders());
    report.setDistancePerVehicle(metricsEventHandler.getDistancePerVehicle());
    report.setUtilizationPerVehicle(metricsEventHandler.getUtilizationPerVehicle());
    report.setDepotDispatchCount(metricsEventHandler.getDepotDispatchCount());
    report.setDepotStockChange(metricsEventHandler.getDepotStockChange());

    return ResponseEntity.ok(report);
  }

  private double computeVehicleUtilizationPercentage() {
    long ticks = Math.max(1, simulationEngine.getCurrentTick());
    java.util.List<?> vehicles = simulationEngine.getVehicles();
    int vehicleCount = Math.max(1, vehicles == null ? 0 : vehicles.size());
    long totalUtilizationTicks = metricsEventHandler.getUtilizationPerVehicle().values().stream()
        .mapToLong(Long::longValue)
        .sum();
    double ratio = (double) totalUtilizationTicks / (ticks * vehicleCount * com.tezprojesi.simulation.SimulationEngine.TICK_MINUTES);
    return Math.max(0.0, Math.min(100.0, ratio * 100.0));
  }
}

