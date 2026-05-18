package com.tezprojesi.api.controller;

import com.tezprojesi.api.dto.KpiDto;
import com.tezprojesi.api.dto.SimulationDashboardDto;
import com.tezprojesi.api.dto.SimulationEventDto;
import com.tezprojesi.api.dto.SimulationStatusDto;
import com.tezprojesi.api.dto.SimulationSummaryDto;
import com.tezprojesi.api.dto.VehicleAssignmentDto;
import com.tezprojesi.api.dto.VehicleDispatchReadinessDto;
import com.tezprojesi.api.exception.BadRequestException;
import com.tezprojesi.domain.OrderStatus;
import com.tezprojesi.simulation.MetricsEventHandler;
import com.tezprojesi.simulation.SimulationEngine;
import com.tezprojesi.simulation.SimulationEventLogService;
import com.tezprojesi.simulation.VehicleMovementService;
import com.tezprojesi.simulation.VehicleReportService;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/simulation")
public class SimulationController {

  private final SimulationEngine simulationEngine;
  private final MetricsEventHandler metricsEventHandler;
  private final VehicleReportService vehicleReportService;
  private final SimulationEventLogService eventLogService;
  private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM HH:mm");

  public SimulationController(
      SimulationEngine simulationEngine,
      MetricsEventHandler metricsEventHandler,
      VehicleReportService vehicleReportService,
      SimulationEventLogService eventLogService) {
    this.simulationEngine = Objects.requireNonNull(simulationEngine, "simulationEngine is required");
    this.metricsEventHandler = Objects.requireNonNull(metricsEventHandler, "metricsEventHandler is required");
    this.vehicleReportService = Objects.requireNonNull(vehicleReportService, "vehicleReportService is required");
    this.eventLogService = Objects.requireNonNull(eventLogService, "eventLogService is required");
  }

  @PostMapping("/start")
  public ResponseEntity<Void> start() {
    if (simulationEngine.hasFailure()) {
      throw new BadRequestException("Simulation is in error state. Reset is required before starting again.");
    }
    if (simulationEngine.isRunning()) {
      return ResponseEntity.accepted().build();
    }
    simulationEngine.startAsync();
    return ResponseEntity.accepted().build();
  }

  @PostMapping("/stop")
  public ResponseEntity<Void> stop() {
    if (!simulationEngine.isRunning()) {
      return ResponseEntity.ok().build();
    }
    simulationEngine.stop();
    return ResponseEntity.ok().build();
  }

  @PostMapping("/reset")
  public ResponseEntity<Void> reset() {
    if (simulationEngine.isRunning()) {
      return ResponseEntity.badRequest().build();
    }
    simulationEngine.reset();
    return ResponseEntity.ok().build();
  }

  @PostMapping("/restart")
  public ResponseEntity<Void> restart() {
    simulationEngine.resetAndRestart();
    return ResponseEntity.accepted().build();
  }

  @PostMapping("/advance")
  public ResponseEntity<Void> advance(@RequestParam(defaultValue = "60") long ticks) {
    if (simulationEngine.isRunning()) {
      return ResponseEntity.badRequest().build();
    }
    if (simulationEngine.hasFailure()) {
      throw new BadRequestException("Simulation is in error state. Reset is required before advancing time.");
    }
    try {
      simulationEngine.stepTicks(ticks);
    } catch (RuntimeException exception) {
      simulationEngine.recordFailure(exception);
      throw exception;
    }
    return ResponseEntity.ok().build();
  }

  @GetMapping("/status")
  public ResponseEntity<SimulationStatusDto> status() {
    SimulationStatusDto status = new SimulationStatusDto();
    status.setRunning(simulationEngine.isRunning());
    if (simulationEngine.isRunning()) {
      status.setPhase("RUNNING");
    } else if (simulationEngine.hasFailure()) {
      status.setPhase("ERROR");
    } else if (simulationEngine.getCurrentTick() <= 0) {
      status.setPhase("READY");
    } else if (simulationEngine.getCurrentTick() >= simulationEngine.getMaxTicks()) {
      status.setPhase("COMPLETED");
    } else {
      status.setPhase("PAUSED");
    }
    status.setCurrentTick(simulationEngine.getCurrentTick());
    status.setMaxTicks(simulationEngine.getMaxTicks());
    status.setErrorMessage(simulationEngine.getLastFailureMessage());
    status.setFailureTick(simulationEngine.getLastFailureTick());
    status.setPendingOrders(resolveOpenOrderCount());
    List<com.tezprojesi.domain.Vehicle> vehicles = simulationEngine.getVehicles();
    status.setActiveVehicles(vehicles == null ? 0 : (int) vehicles.stream()
        .filter(vehicle -> vehicle.getStatus() != com.tezprojesi.domain.VehicleStatus.IDLE)
        .count());
    status.setReorderPointFraction(simulationEngine.getReorderPointFraction());
    return ResponseEntity.ok(status);
  }

  @GetMapping("/vehicle-assignments")
  public ResponseEntity<List<VehicleAssignmentDto>> vehicleAssignments() {
    List<VehicleAssignmentDto> result = simulationEngine.getVehicleAssignments().stream().map(this::toVehicleAssignmentDto).toList();
    return ResponseEntity.ok(result);
  }

  @GetMapping("/dispatch-readiness")
  public ResponseEntity<List<VehicleDispatchReadinessDto>> dispatchReadiness() {
    List<VehicleDispatchReadinessDto> result = simulationEngine.getDispatchReadiness().stream()
        .map(this::toVehicleDispatchReadinessDto)
        .toList();
    return ResponseEntity.ok(result);
  }

  @PostMapping("/dispatch-ready")
  public ResponseEntity<Void> dispatchReady(@RequestParam String vehicleId) {
    try {
      simulationEngine.approveReadyDispatch(vehicleId);
    } catch (IllegalStateException exception) {
      throw new BadRequestException(exception.getMessage());
    }
    return ResponseEntity.ok().build();
  }

  /**
   * Universal human-in-the-loop approval signal.
   * Works for any vehicle in WAITING_FOR_APPROVAL state, whether it was escalated via
   * the requireManualDispatchApproval flag, a critical cost threshold, or a route deviation >50%.
   * The frontend calls this endpoint to release the dispatch lock.
   */
  @PostMapping("/approve-dispatch")
  public ResponseEntity<Void> approveDispatch(@RequestParam String vehicleId) {
    try {
      simulationEngine.approveDispatch(vehicleId);
    } catch (IllegalStateException exception) {
      throw new BadRequestException(exception.getMessage());
    }
    return ResponseEntity.ok().build();
  }

  @GetMapping("/kpis")
  public ResponseEntity<KpiDto> kpis() {
    KpiDto dto = new KpiDto();
    dto.setTotalOrders(metricsEventHandler.getTotalOrders());
    dto.setCompletedOrders(metricsEventHandler.getCompletedOrders());
    dto.setTotalDistance(metricsEventHandler.getTotalDistance());
    dto.setAvgDeliveryTime(metricsEventHandler.getAverageDeliveryTicks());
    dto.setVehicleUtilization(computeVehicleUtilizationPercentage());
    dto.setDepotStock(simulationEngine.getDepotStock());
    return ResponseEntity.ok(dto);
  }

  @GetMapping("/timeline")
  public ResponseEntity<List<SimulationEventDto>> timeline(@RequestParam(defaultValue = "60") int limit) {
    List<SimulationEventDto> result = eventLogService.latest(limit).stream()
        .map(event -> {
          SimulationEventDto dto = new SimulationEventDto();
          dto.setId(event.id());
          dto.setTick(event.tick());
          dto.setTimeLabel(toTimeLabel(event.tick()));
          dto.setType(event.type());
          dto.setTitle(event.title());
          dto.setDetail(event.detail());
          dto.setEntityType(event.entityType());
          dto.setEntityId(event.entityId());
          return dto;
        })
        .toList();
    return ResponseEntity.ok(result);
  }

  @GetMapping("/summary")
  public ResponseEntity<SimulationSummaryDto> summary() {
    SimulationSummaryDto dto = new SimulationSummaryDto();
    List<VehicleReportService.TripRecord> records = vehicleReportService.getTripRecords();
    dto.setTotalTrips(records.size());
    double totalActualCost = records.stream().mapToDouble(VehicleReportService.TripRecord::cost).sum();
    dto.setTotalCost(totalActualCost);
    dto.setTotalOperationTicks(records.stream().mapToLong(record -> Math.max(0L, record.endTick() - record.startTick())).sum());
    dto.setVehicleUtilizationPercentage(computeVehicleUtilizationPercentage());
    dto.setAverageDeliveryTicks(metricsEventHandler.getAverageDeliveryTicks());
    
    // Detailed Cost Totals
    dto.setTotalFuelCost(records.stream().mapToDouble(VehicleReportService.TripRecord::fuelCost).sum());
    dto.setTotalTollCost(records.stream().mapToDouble(VehicleReportService.TripRecord::tollCost).sum());
    dto.setTotalLaborCost(records.stream().mapToDouble(r -> r.handlingCost() + r.timeCost()).sum());
    dto.setTotalPenaltyCost(records.stream().mapToDouble(r -> r.delayPenalty() + r.stockoutPenalty()).sum());
    dto.setTotalCargoValue(records.stream().mapToDouble(VehicleReportService.TripRecord::totalCargoValue).sum());

    // Sustainability Analytics (kg CO2)
    double totalCo2 = 0;
    double savedCo2 = 0;
    for (VehicleReportService.TripRecord r : records) {
      double factor = "ELECTRIC".equalsIgnoreCase(r.fuelType()) ? 0.0 : ("GASOLINE".equalsIgnoreCase(r.fuelType()) ? 0.7 : 0.8);
      double currentTripCo2 = r.distanceKm() * factor;
      totalCo2 += currentTripCo2;
      // Calculate what would have been emitted if it were Diesel (Baseline)
      if (factor == 0.0) {
        savedCo2 += (r.distanceKm() * 0.8);
      }
    }
    dto.setCo2Emissions(totalCo2);
    dto.setCo2Saved(savedCo2);

    dto.setMultiStopTrips(records.stream().filter(record -> record.stopCount() > 1).count());
    dto.setSingleStopTrips(records.stream().filter(record -> record.stopCount() <= 1).count());
    
    double multiCost = records.stream().filter(record -> record.stopCount() > 1).mapToDouble(VehicleReportService.TripRecord::cost).average().orElse(0.0);
    double singleCost = records.stream().filter(record -> record.stopCount() <= 1).mapToDouble(VehicleReportService.TripRecord::cost).average().orElse(0.0);
    dto.setMultiStopAverageCost(multiCost);
    dto.setSingleStopAverageCost(singleCost);

    // Efficiency ROI calculation
    if (singleCost > 0) {
      double hypotheticalSingleStopTotal = singleCost * records.size();
      dto.setTotalSavings(Math.max(0, hypotheticalSingleStopTotal - totalActualCost));
    }

    if (multiCost > 0 && singleCost > 0 && multiCost < singleCost) {
      double gain = ((singleCost - multiCost) / singleCost) * 100.0;
      dto.setComparisonSummary(String.format(java.util.Locale.US, "Multi-stop planlar ortalamada %%.1f daha dusuk maliyet uretti.", gain));
    } else if (singleCost > 0) {
      dto.setComparisonSummary("Single-stop planlar daha verimli bulundu veya veri henuz karsilastirma icin yeterli degil.");
    } else {
      dto.setComparisonSummary("Karsilastirma icin yeterli replenishment gorevi henuz olusmadi.");
    }
    return ResponseEntity.ok(dto);
  }

  private int resolveOpenOrderCount() {
    int pendingOrders = simulationEngine.getPendingOrderCount();
    if (pendingOrders > 0) {
      return pendingOrders;
    }
    java.util.Map<String, com.tezprojesi.domain.Order> activeOrders = simulationEngine.getActiveOrders();
    if (activeOrders == null) {
      return 0;
    }
    return (int) activeOrders.values().stream()
        .filter(order -> order.getStatus() != OrderStatus.COMPLETED)
        .filter(order -> order.getStatus() != OrderStatus.FAILED)
        .count();
  }

  private double computeVehicleUtilizationPercentage() {
    long ticks = Math.max(1, simulationEngine.getCurrentTick());
    List<?> vehicles = simulationEngine.getVehicles();
    int vehicleCount = Math.max(1, vehicles == null ? 0 : vehicles.size());
    long totalUtilizationTicks = metricsEventHandler.getUtilizationPerVehicle().values().stream()
        .mapToLong(Long::longValue)
        .sum();
    double ratio = (double) totalUtilizationTicks / (ticks * vehicleCount * com.tezprojesi.simulation.SimulationEngine.TICK_MINUTES);
    return Math.max(0.0, Math.min(100.0, ratio * 100.0));
  }

  private String toTimeLabel(long tick) {
    return LocalDateTime.of(2026, 3, 29, 8, 0)
        .plusMinutes(Math.max(0L, tick) * com.tezprojesi.simulation.SimulationEngine.TICK_MINUTES)
        .format(TIME_FORMATTER);
  }

  private VehicleAssignmentDto toVehicleAssignmentDto(VehicleMovementService.AssignmentSnapshot snapshot) {
    VehicleAssignmentDto dto = new VehicleAssignmentDto();
    dto.setVehicleId(snapshot.vehicleId());
    dto.setReferenceId(snapshot.referenceId());
    dto.setRouteType(snapshot.routeType());
    dto.setPlannedNodeIds(snapshot.plannedNodeIds());
    dto.setRemainingNodeIds(snapshot.remainingNodeIds());
    dto.setActiveDepotId(snapshot.activeDepotId());
    dto.setRemainingStopCount(snapshot.remainingStopCount());
    dto.setLoadFraction(snapshot.loadFraction());
    dto.setTotalVolume(snapshot.totalVolume());
    dto.setCapacityVolume(snapshot.capacityVolume());
    dto.setDistanceKm(snapshot.distanceKm());
    dto.setWaitTicks(snapshot.waitTicks());
    dto.setTotalLoadingTicks(snapshot.totalLoadingTicks());
    dto.setTotalUnloadingTicks(snapshot.totalUnloadingTicks());
    dto.setLoadingRemainingTicks(snapshot.loadingRemainingTicks());
    dto.setUnloadingRemainingTicks(snapshot.unloadingRemainingTicks());
    dto.setCurrentFromNodeId(snapshot.currentFromNodeId());
    dto.setCurrentToNodeId(snapshot.currentToNodeId());
    dto.setSegmentRemainingTicks(snapshot.segmentRemainingTicks());
    dto.setSegmentTotalTicks(snapshot.segmentTotalTicks());
    dto.setRemainingTotalTicks(snapshot.remainingTotalTicks());
    dto.setStartedAtTick(snapshot.startedAtTick());
    dto.setExpectedCompletionTick(snapshot.expectedCompletionTick());
    dto.setStatusDetail(snapshot.statusDetail());
    dto.setDecisionReason(snapshot.decisionReason());
    dto.setDepartureReason(snapshot.departureReason());
    dto.setRouteSummary(snapshot.routeSummary());
    dto.setCargoSummary(snapshot.cargoSummary());
    dto.setComparisonSummary(snapshot.comparisonSummary());
    dto.setPlanCost(snapshot.planCost());
    dto.setPlanBenefit(snapshot.planBenefit());
    dto.setNetScore(snapshot.netScore());
    dto.setPlannedStopCount(snapshot.plannedStopCount());
    dto.setMissionTicks(snapshot.missionTicks());
    dto.setDeliveryDistanceKm(snapshot.deliveryDistanceKm());
    dto.setRepositionDistanceKm(snapshot.repositionDistanceKm());
    dto.setRouteCost(snapshot.routeCost());
    dto.setHandlingCost(snapshot.handlingCost());
    dto.setTimeCost(snapshot.timeCost());
    dto.setExtraStopCost(snapshot.extraStopCost());
    dto.setDelayPenalty(snapshot.delayPenalty());
    dto.setStockoutPenalty(snapshot.stockoutPenalty());
    dto.setOpportunityCost(snapshot.opportunityCost());
    return dto;
  }

  private VehicleDispatchReadinessDto toVehicleDispatchReadinessDto(SimulationEngine.DispatchReadinessSnapshot snapshot) {
    VehicleDispatchReadinessDto dto = new VehicleDispatchReadinessDto();
    dto.setVehicleId(snapshot.vehicleId());
    dto.setReferenceId(snapshot.referenceId());
    dto.setRequestIds(snapshot.requestIds());
    dto.setTargetDepotIds(snapshot.targetDepotIds());
    dto.setTotalVolume(snapshot.totalVolume());
    dto.setCapacityVolume(snapshot.capacityVolume());
    dto.setLoadFraction(snapshot.loadFraction());
    dto.setMinimumDepartureLoadFraction(snapshot.minimumDepartureLoadFraction());
    dto.setImmediateDepartureLoadFraction(snapshot.immediateDepartureLoadFraction());
    dto.setMinimumLoadReached(snapshot.minimumLoadReached());
    dto.setWaitTicks(snapshot.waitTicks());
    dto.setMaxWaitTicks(snapshot.maxWaitTicks());
    dto.setMaxWaitReached(snapshot.maxWaitReached());
    dto.setUrgent(snapshot.urgent());
    dto.setReadyToDispatch(snapshot.readyToDispatch());
    dto.setAwaitingApproval(snapshot.awaitingApproval());
    dto.setCapacityApprovalPending(
        snapshot.awaitingApproval()
            && snapshot.loadFraction() >= 0.5
            && snapshot.loadFraction() < 0.75);
    dto.setFirstRequestTick(snapshot.firstRequestTick());
    dto.setLastRequestUpdateTick(snapshot.lastRequestUpdateTick());
    dto.setRouteSummary(snapshot.routeSummary());
    dto.setCargoSummary(snapshot.cargoSummary());
    dto.setDecisionReason(snapshot.decisionReason());
    dto.setComparisonSummary(snapshot.comparisonSummary());
    dto.setStopCount(snapshot.stopCount());
    dto.setMissionTicks(snapshot.missionTicks());
    dto.setDeliveryDistanceKm(snapshot.deliveryDistanceKm());
    dto.setRepositionDistanceKm(snapshot.repositionDistanceKm());
    dto.setPlanCost(snapshot.planCost());
    dto.setPlanBenefit(snapshot.planBenefit());
    dto.setNetScore(snapshot.netScore());
    dto.setRouteCost(snapshot.routeCost());
    dto.setHandlingCost(snapshot.handlingCost());
    dto.setTimeCost(snapshot.timeCost());
    dto.setExtraStopCost(snapshot.extraStopCost());
    dto.setDelayPenalty(snapshot.delayPenalty());
    dto.setStockoutPenalty(snapshot.stockoutPenalty());
    dto.setOpportunityCost(snapshot.opportunityCost());
    return dto;
  }
}
