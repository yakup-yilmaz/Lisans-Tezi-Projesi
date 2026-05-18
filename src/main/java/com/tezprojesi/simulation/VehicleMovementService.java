package com.tezprojesi.simulation;

import com.tezprojesi.domain.OrderLine;
import com.tezprojesi.domain.Vehicle;
import com.tezprojesi.domain.VehicleStatus;
import com.tezprojesi.events.EventBus;
import com.tezprojesi.routing.CityGraph;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class VehicleMovementService {

  private static final long DISPLAY_TICK_MINUTES = SimulationEngine.TICK_MINUTES;
  private long currentTick = 0;

  public void setCurrentTick(long currentTick) {
    this.currentTick = currentTick;
  }

  private final Map<String, Assignment> assignmentsByVehicle = new HashMap<>();
  private final EventBus eventBus;
  private final SimulationEventLogService eventLogService;

  public VehicleMovementService(EventBus eventBus, SimulationEventLogService eventLogService) {
    this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
    this.eventLogService = Objects.requireNonNull(eventLogService, "eventLogService");
  }

  public void assignRoute(
      String vehicleId,
      String referenceId,
      List<String> plannedNodeIds,
      List<SegmentPlan> segmentPlans,
      List<StopPlan> stopPlans,
      long startTick,
      double volume,
      String routeType,
      double distanceKm,
      double cost,
      double capacityVolume,
      double loadFraction,
      long waitTicks,
      long loadingTicks,
      String decisionReason,
      String departureReason,
      String routeSummary,
      String cargoSummary,
      String comparisonSummary,
      double planCost,
      double planBenefit,
      double netScore,
      long missionTicks,
      double deliveryDistanceKm,
      double repositionDistanceKm,
      double routeCost,
      double handlingCost,
      double timeCost,
      double extraStopCost,
      double delayPenalty,
      double stockoutPenalty,
      double opportunityCost) {
    Objects.requireNonNull(vehicleId, "vehicleId");
    Objects.requireNonNull(referenceId, "referenceId");
    Objects.requireNonNull(plannedNodeIds, "plannedNodeIds");
    Objects.requireNonNull(segmentPlans, "segmentPlans");
    if (segmentPlans.isEmpty()) {
      throw new IllegalArgumentException("segmentPlans must not be empty");
    }
    assignmentsByVehicle.put(vehicleId, new Assignment(
        referenceId,
        new ArrayDeque<>(segmentPlans),
        new ArrayDeque<>(stopPlans),
        startTick,
        volume,
        routeType,
        List.copyOf(plannedNodeIds),
        distanceKm,
        cost,
        capacityVolume,
        loadFraction,
        waitTicks,
        stopPlans.size(),
        loadingTicks,
        0,
        loadingTicks,
        0,
        null,
        null,
        decisionReason,
        departureReason,
        routeSummary,
        cargoSummary,
        comparisonSummary,
        planCost,
        planBenefit,
        netScore,
        missionTicks,
        deliveryDistanceKm,
        repositionDistanceKm,
        routeCost,
        handlingCost,
        timeCost,
        extraStopCost,
        delayPenalty,
        stockoutPenalty,
        opportunityCost));
    if (loadingTicks > 0) {
      eventLogService.record(startTick, "VEHICLE_LOADING_STARTED", "Yukleme basladi", vehicleId + " araci ana depoda yukleniyor. " + cargoSummary, "VEHICLE", vehicleId);
    } else {
      eventLogService.record(startTick, "VEHICLE_DEPARTED", "Arac yola cikti", vehicleId + " " + routeSummary + ". " + departureReason, "VEHICLE", vehicleId);
    }
  }

  public List<TickResult> tick(List<Vehicle> vehicles, long currentTick, CityGraph graph) {
    Objects.requireNonNull(graph, "graph");
    List<TickResult> results = new ArrayList<>();
    for (Vehicle vehicle : vehicles) {
      Assignment assignment = assignmentsByVehicle.get(vehicle.getId());
      if (assignment == null) {
        continue;
      }

      List<CompletedStop> completedStops = new ArrayList<>();

      if (assignment.loadingRemainingTicks() > 0) {
        assignment = assignment.withLoadingRemainingTicks(assignment.loadingRemainingTicks() - 1);
        vehicle.setStatus(VehicleStatus.LOADING);
        assignmentsByVehicle.put(vehicle.getId(), assignment);
        if (assignment.loadingRemainingTicks() == 0) {
          eventLogService.record(currentTick, "VEHICLE_LOADING_COMPLETED", "Yukleme tamamlandi", vehicle.getId() + " araci yuklemeyi tamamlayip cikisa hazir. " + assignment.cargoSummary(), "VEHICLE", vehicle.getId());
        }
        results.add(new TickResult(vehicle.getId(), completedStops, null));
        continue;
      }

      if (assignment.unloadingRemainingTicks() > 0) {
        assignment = assignment.withUnloadingRemainingTicks(assignment.unloadingRemainingTicks() - 1);
        vehicle.setStatus(VehicleStatus.DELIVERING);
        assignmentsByVehicle.put(vehicle.getId(), assignment);
        if (assignment.unloadingRemainingTicks() > 0) {
          results.add(new TickResult(vehicle.getId(), completedStops, null));
          continue;
        }
        if (assignment.activeStop() != null) {
          completedStops.add(new CompletedStop(
              vehicle.getId(),
              assignment.referenceId(),
              assignment.activeStop().requestIds(),
              assignment.activeStop().depotId(),
              assignment.activeStop().items(),
              assignment.activeStop().volume(),
              currentTick));
          eventLogService.record(currentTick, "VEHICLE_UNLOADING_COMPLETED", "Bosaltma tamamlandi", vehicle.getId() + " " + assignment.activeStop().depotId() + " deposunda bosaltmayi tamamladi.", "VEHICLE", vehicle.getId());
          assignment = assignment.withActiveStop(null);
          assignmentsByVehicle.put(vehicle.getId(), assignment);
        }
      }

      if (assignment.activeSegment() != null) {
        SegmentProgress segment = assignment.activeSegment();
        vehicle.setStatus("RETURN".equals(assignment.routeType()) ? VehicleStatus.RETURNING : VehicleStatus.EN_ROUTE);
        var edge = graph.getEdge(segment.fromNodeId(), segment.toNodeId());
        if (edge == null) {
          throw new IllegalStateException("Active segment edge is missing: " + segment.fromNodeId() + " -> " + segment.toNodeId());
        }
        double distancePerTickKm = distancePerTickKm(vehicle, edge.getTrafficFactor());
        double remainingDistanceKm = Math.max(0.0, segment.remainingDistanceKm() - distancePerTickKm);
        // Logistics rule: rerouting is forbidden after departure; only the active segment duration
        // is recalculated from the current traffic factor.
        long refreshedTotalTicks = computeTravelTicks(segment.distanceKm(), edge.getTrafficFactor(), vehicle.getAverageSpeedKmPerHour());
        long refreshedRemainingTicks = computeTravelTicks(remainingDistanceKm, edge.getTrafficFactor(), vehicle.getAverageSpeedKmPerHour());
        assignment = assignment.withActiveSegment(segment.withProgress(remainingDistanceKm, refreshedRemainingTicks, refreshedTotalTicks));
        if (remainingDistanceKm > 0.0001) {
          assignmentsByVehicle.put(vehicle.getId(), assignment);
          results.add(new TickResult(vehicle.getId(), completedStops, null));
          continue;
        }

        String reachedNode = segment.toNodeId();
        vehicle.setLocationNodeId(reachedNode);
        assignment = assignment.withActiveSegment(null);

        StopPlan nextStop = assignment.stopPlans().peekFirst();
        if (nextStop != null && nextStop.nodeId().equals(reachedNode)) {
          assignment.stopPlans().pollFirst();
          assignment = assignment.withUnloadingRemainingTicks(nextStop.unloadTicks())
              .withTotalUnloadingTicks(assignment.totalUnloadingTicks() + nextStop.unloadTicks())
              .withActiveStop(nextStop);
          eventLogService.record(currentTick, "VEHICLE_ARRIVED", "Arac depoya ulasti", vehicle.getId() + " " + nextStop.depotId() + " deposuna ulasti.", "VEHICLE", vehicle.getId());
          eventLogService.record(currentTick, "VEHICLE_UNLOADING_STARTED", "Bosaltma basladi", vehicle.getId() + " " + nextStop.depotId() + " deposunda bosaltmaya basladi.", "VEHICLE", vehicle.getId());
        }
        assignmentsByVehicle.put(vehicle.getId(), assignment);
        results.add(new TickResult(vehicle.getId(), completedStops, null));
        continue;
      }

      CompletedAssignment completedAssignment = null;
      if (assignment.segmentPlans().isEmpty()) {
        completedAssignment = new CompletedAssignment(
            vehicle.getId(),
            assignment.referenceId(),
            assignment.startedAtTick(),
            currentTick,
            assignment.volume(),
            assignment.routeType(),
            assignment.distanceKm(),
            assignment.cost(),
            assignment.loadFraction(),
            assignment.waitTicks(),
            assignment.totalLoadingTicks(),
            assignment.totalUnloadingTicks(),
            assignment.totalStopCount(),
            assignment.planBenefit(),
            assignment.netScore(),
            assignment.routeCost(),      // Added
            assignment.handlingCost(),   // Added
            assignment.timeCost(),       // Added
            assignment.extraStopCost(),  // Added
            assignment.delayPenalty(),   // Added
            assignment.stockoutPenalty()); // Added
        assignmentsByVehicle.remove(vehicle.getId());
        eventLogService.record(currentTick, "VEHICLE_ROUTE_COMPLETED", "Arac gorevi tamamladi", vehicle.getId() + " gorevini tamamladi. " + assignment.routeSummary(), "VEHICLE", vehicle.getId());
        results.add(new TickResult(vehicle.getId(), completedStops, completedAssignment));
        continue;
      }

      SegmentPlan nextSegment = assignment.segmentPlans().pollFirst();
      assignment = assignment.withActiveSegment(new SegmentProgress(
          nextSegment.fromNodeId(),
          nextSegment.toNodeId(),
          nextSegment.distanceKm(),
          nextSegment.distanceKm(),
          nextSegment.travelTicks(),
          nextSegment.travelTicks()));
      vehicle.setStatus("RETURN".equals(assignment.routeType()) ? VehicleStatus.RETURNING : VehicleStatus.EN_ROUTE);
      assignmentsByVehicle.put(vehicle.getId(), assignment);
      if (assignment.activeSegment().totalTicks() == nextSegment.travelTicks()) {
        eventLogService.record(currentTick, "VEHICLE_DEPARTED", "Arac yola cikti", vehicle.getId() + " " + assignment.routeSummary() + ". " + assignment.departureReason(), "VEHICLE", vehicle.getId());
      }
      results.add(new TickResult(vehicle.getId(), completedStops, completedAssignment));
    }
    return results;
  }

  public boolean hasActiveRoute(String vehicleId) {
    return assignmentsByVehicle.containsKey(vehicleId);
  }

  public void refreshTravelDurations(CityGraph graph, List<Vehicle> vehicles) {
    Objects.requireNonNull(graph, "graph");
    Map<String, Vehicle> vehiclesById = vehicles.stream()
        .collect(java.util.stream.Collectors.toMap(Vehicle::getId, vehicle -> vehicle, (left, right) -> left));

    assignmentsByVehicle.replaceAll((vehicleId, assignment) -> {
      Vehicle vehicle = vehiclesById.get(vehicleId);
      if (vehicle == null) {
        return assignment;
      }
      return refreshAssignmentTravelDurations(graph, vehicle, assignment);
    });
  }

  public Optional<String> getCurrentNodeId(String vehicleId) {
    Assignment assignment = assignmentsByVehicle.get(vehicleId);
    if (assignment == null) {
      return Optional.empty();
    }
    if (assignment.activeStop() != null) {
      return Optional.of(assignment.activeStop().nodeId());
    }
    if (assignment.activeSegment() != null) {
      return Optional.of(assignment.activeSegment().fromNodeId());
    }
    SegmentPlan nextSegment = assignment.segmentPlans().peekFirst();
    if (nextSegment != null) {
      return Optional.of(nextSegment.fromNodeId());
    }
    return Optional.ofNullable(assignment.plannedNodeIds().isEmpty() ? null : assignment.plannedNodeIds().get(assignment.plannedNodeIds().size() - 1));
  }

  public List<AssignmentSnapshot> getActiveAssignments() {
    return assignmentsByVehicle.entrySet().stream()
        .map(entry -> {
          Assignment assignment = entry.getValue();
          long segmentTotalTicks = resolveSegmentTotalTicks(assignment);
          long segmentRemainingTicks = resolveSegmentRemainingTicks(assignment);
          long remainingTotalTicks = assignment.loadingRemainingTicks() + assignment.unloadingRemainingTicks();
          if (assignment.activeSegment() != null) {
            remainingTotalTicks += assignment.activeSegment().remainingTicks();
          }
          remainingTotalTicks += assignment.segmentPlans().stream().mapToLong(SegmentPlan::travelTicks).sum();
          remainingTotalTicks += assignment.stopPlans().stream().mapToLong(StopPlan::unloadTicks).sum();
          return new AssignmentSnapshot(
              entry.getKey(),
              assignment.referenceId(),
              assignment.routeType(),
              assignment.plannedNodeIds(),
              remainingNodeIds(assignment),
              assignment.activeStop() == null ? null : assignment.activeStop().depotId(),
              assignment.stopPlans().size() + (assignment.activeStop() == null ? 0 : 1),
              assignment.loadFraction(),
              assignment.volume(),
              assignment.capacityVolume(),
              assignment.distanceKm(),
              assignment.waitTicks(),
              assignment.totalLoadingTicks(),
              assignment.totalUnloadingTicks(),
              assignment.loadingRemainingTicks(),
              assignment.unloadingRemainingTicks(),
              resolveSnapshotFromNode(assignment),
              resolveSnapshotToNode(assignment),
              segmentRemainingTicks,
              segmentTotalTicks,
              remainingTotalTicks,
              assignment.startedAtTick(),
              assignment.startedAtTick() + remainingTotalTicks,
              buildStatusDetail(assignment),
              assignment.decisionReason(),
              assignment.departureReason(),
              assignment.routeSummary(),
              assignment.cargoSummary(),
              assignment.comparisonSummary(),
              assignment.planCost(),
              assignment.planBenefit(),
              assignment.netScore(),
              assignment.totalStopCount(),
              assignment.missionTicks(),
              assignment.deliveryDistanceKm(),
              assignment.repositionDistanceKm(),
              assignment.routeCost(),
              assignment.handlingCost(),
              assignment.timeCost(),
              assignment.extraStopCost(),
              assignment.delayPenalty(),
              assignment.stockoutPenalty(),
              assignment.opportunityCost());
        })
        .toList();
  }

  private String resolveCurrentFromNode(Assignment assignment) {
    // 1. Active segment varsa, şu an hareket halindeki segment'in başlangıç node'u
    if (assignment.activeSegment() != null) {
      return assignment.activeSegment().fromNodeId();
    }
    
    // 2. Loading/waiting sırasında, bir sonraki segment'in başlangıç node'u
    SegmentPlan nextSegment = assignment.segmentPlans().peekFirst();
    if (nextSegment != null) {
      return nextSegment.fromNodeId();
    }
    
    // 3. Segment yok ama planlanan node'lar var: loading sırasında ilk hedef node
    if (!assignment.plannedNodeIds().isEmpty()) {
      return assignment.plannedNodeIds().get(0);
    }
    
    // 4. Unloading sırasında, depo node'u
    if (assignment.activeStop() != null) {
      return assignment.activeStop().nodeId();
    }
    
    return null;
  }

  private String resolveSnapshotFromNode(Assignment assignment) {
    if (assignment.activeSegment() != null) {
      return assignment.activeSegment().fromNodeId();
    }
    if (assignment.activeStop() != null) {
      return assignment.activeStop().nodeId();
    }
    return resolveCurrentFromNode(assignment);
  }

  private String resolveCurrentToNode(Assignment assignment) {
    // 1. Active segment varsa, hareket halindeki segment'in bitiş node'u
    if (assignment.activeSegment() != null) {
      return assignment.activeSegment().toNodeId();
    }
    
    // 2. Loading/waiting sırasında, bir sonraki segment'in bitiş node'u
    SegmentPlan nextSegment = assignment.segmentPlans().peekFirst();
    if (nextSegment != null) {
      return nextSegment.toNodeId();
    }
    
    // 3. Segment yok ama planlanan node'lar var: loading sırasında ikinci hedef (varsa)
    if (!assignment.plannedNodeIds().isEmpty() && assignment.plannedNodeIds().size() > 1) {
      return assignment.plannedNodeIds().get(1);
    }
    
    // 4. Unloading veya durum net değil: depo node'u dön
    if (assignment.activeStop() != null) {
      return assignment.activeStop().nodeId();
    }
    
    // 5. Tek node varsa (loading finished, route tamamlanmak üzere): aynı node'u dön
    if (!assignment.plannedNodeIds().isEmpty()) {
      return assignment.plannedNodeIds().get(0);
    }
    
    return null;
  }

  private String resolveSnapshotToNode(Assignment assignment) {
    if (assignment.activeSegment() != null) {
      return assignment.activeSegment().toNodeId();
    }
    if (assignment.activeStop() != null) {
      return assignment.activeStop().nodeId();
    }
    return resolveCurrentToNode(assignment);
  }

  private long resolveSegmentTotalTicks(Assignment assignment) {
    if (assignment.activeSegment() != null) {
      return assignment.activeSegment().totalTicks();
    }
    SegmentPlan nextSegment = assignment.segmentPlans().peekFirst();
    if (nextSegment != null) {
      return nextSegment.travelTicks();
    }
    return 0L;
  }

  private long resolveSegmentRemainingTicks(Assignment assignment) {
    if (assignment.activeSegment() != null) {
      return assignment.activeSegment().remainingTicks();
    }
    if (assignment.loadingRemainingTicks() > 0) {
      return assignment.loadingRemainingTicks();
    }
    SegmentPlan nextSegment = assignment.segmentPlans().peekFirst();
    if (nextSegment != null) {
      return nextSegment.travelTicks();
    }
    return 0L;
  }

  private String buildStatusDetail(Assignment assignment) {
    if (assignment.loadingRemainingTicks() > 0) {
      return "Yukleniyor - " + toMinutes(assignment.loadingRemainingTicks()) + "/" + toMinutes(assignment.totalLoadingTicks()) + " dk kaldi ("
          + formatTickToTime(assignment.startedAtTick() + assignment.totalLoadingTicks()) + " Hazir)";
    }
    if (assignment.unloadingRemainingTicks() > 0) {
      return "Bosaltiyor - " + toMinutes(assignment.unloadingRemainingTicks()) + " dk kaldi";
    }
    if (assignment.activeSegment() != null) {
      long remaining = assignment.activeSegment().remainingTicks();
      for (SegmentPlan p : assignment.segmentPlans()) {
        remaining += p.travelTicks();
      }
      for (StopPlan s : assignment.stopPlans()) {
        remaining += s.unloadTicks();
      }
      return "Yolda - " + toMinutes(assignment.activeSegment().remainingTicks()) + " dk / T. Varis: " 
          + formatTickToTime(assignment.startedAtTick() + assignment.missionTicks());
    }
    return "Gorev planlaniyor";
  }

  private String formatTickToTime(long tick) {
    long totalMinutes = 8 * 60 + tick;
    long hours = (totalMinutes / 60) % 24;
    long minutes = totalMinutes % 60;
    return String.format("%02d:%02d", hours, minutes);
  }

  private long toMinutes(long ticks) {
    return Math.max(0L, ticks) * DISPLAY_TICK_MINUTES;
  }

  private String formatVolume(double value) {
    return String.format(java.util.Locale.US, "%.1f", value);
  }

  private List<String> remainingNodeIds(Assignment assignment) {
    List<String> remaining = new ArrayList<>();
    if (assignment.activeSegment() != null) {
      remaining.add(assignment.activeSegment().toNodeId());
    }
    for (SegmentPlan plan : assignment.segmentPlans()) {
      remaining.add(plan.toNodeId());
    }
    return List.copyOf(remaining);
  }

  public void reset() {
    assignmentsByVehicle.clear();
  }

  private Assignment refreshAssignmentTravelDurations(CityGraph graph, Vehicle vehicle, Assignment assignment) {
    ArrayDeque<SegmentPlan> refreshedPlans = new ArrayDeque<>();
    for (SegmentPlan segmentPlan : assignment.segmentPlans()) {
      refreshedPlans.add(refreshSegmentPlan(graph, vehicle, segmentPlan));
    }
    assignment = assignment.withSegmentPlans(refreshedPlans);

    if (assignment.activeSegment() == null) {
      return assignment;
    }

    SegmentProgress currentSegment = assignment.activeSegment();
    var edge = graph.getEdge(currentSegment.fromNodeId(), currentSegment.toNodeId());
    if (edge == null) {
      return assignment;
    }

    long refreshedTotalTicks = computeTravelTicks(
        currentSegment.distanceKm(),
        edge.getTrafficFactor(),
        vehicle.getAverageSpeedKmPerHour());
    long distanceBasedRemainingTicks = computeTravelTicks(
        currentSegment.remainingDistanceKm(),
        edge.getTrafficFactor(),
        vehicle.getAverageSpeedKmPerHour());

    // Logistics rule: route stays fixed, but the unfinished segment is re-timed from the
    // remaining distance using the latest traffic factor instead of recalculating a new path.
    long remainingRouteTicks = distanceBasedRemainingTicks;
    for (SegmentPlan p : refreshedPlans) {
        remainingRouteTicks += p.travelTicks();
    }
    for (StopPlan s : assignment.stopPlans()) {
        remainingRouteTicks += s.unloadTicks();
    }
    if (assignment.activeStop() != null) {
        remainingRouteTicks += assignment.unloadingRemainingTicks();
    }

    // missionTicks is the total estimated time from startedAtTick to completion.
    // We update it based on: ticks already passed + ticks remaining.
    long passedTicks = currentTick - assignment.startedAtTick();
    long updatedMissionTicks = passedTicks + remainingRouteTicks;

    return assignment.withActiveSegment(new SegmentProgress(
        currentSegment.fromNodeId(),
        currentSegment.toNodeId(),
        currentSegment.distanceKm(),
        currentSegment.remainingDistanceKm(),
        distanceBasedRemainingTicks,
        refreshedTotalTicks))
        .withSegmentPlans(refreshedPlans)
        .withMissionTicks(updatedMissionTicks);
  }



  private SegmentPlan refreshSegmentPlan(CityGraph graph, Vehicle vehicle, SegmentPlan segmentPlan) {
    var edge = graph.getEdge(segmentPlan.fromNodeId(), segmentPlan.toNodeId());
    if (edge == null) {
      return segmentPlan;
    }
    return new SegmentPlan(
        segmentPlan.fromNodeId(),
        segmentPlan.toNodeId(),
        segmentPlan.distanceKm(),
        computeTravelTicks(segmentPlan.distanceKm(), edge.getTrafficFactor(), vehicle.getAverageSpeedKmPerHour()));
  }

  private long computeTravelTicks(double distanceKm, double trafficFactor, double averageSpeedKmPerHour) {
    if (distanceKm <= 0.0) {
      return 0L;
    }
    // Logistics rule: CurrentStepTicks = ceil(BaseTicks * currentTrafficFactor).
    long baseTicks = computeBaseTravelTicks(distanceKm, averageSpeedKmPerHour);
    return Math.max(1L, (long) Math.ceil(baseTicks * Math.max(trafficFactor, 0.1)));
  }

  private double distancePerTickKm(Vehicle vehicle, double trafficFactor) {
    double tickDurationHours = DISPLAY_TICK_MINUTES / 60.0;
    double effectiveSpeedKmPerHour = Math.max(0.1, vehicle.getAverageSpeedKmPerHour() / Math.max(0.1, trafficFactor));
    return effectiveSpeedKmPerHour * tickDurationHours;
  }

  private long computeBaseTravelTicks(double distanceKm, double averageSpeedKmPerHour) {
    if (distanceKm <= 0.0) {
      return 0L;
    }
    double tickDurationHours = DISPLAY_TICK_MINUTES / 60.0;
    double effectiveSpeedKmPerHour = Math.max(0.1, averageSpeedKmPerHour);
    return Math.max(1L, (long) Math.ceil((distanceKm / effectiveSpeedKmPerHour) / tickDurationHours));
  }

  public record StopPlan(
      String depotId,
      String nodeId,
      List<String> requestIds,
      List<OrderLine> items,
      double volume,
      long unloadTicks) {}

  public record SegmentPlan(
      String fromNodeId,
      String toNodeId,
      double distanceKm,
      long travelTicks) {}

  public record CompletedStop(
      String vehicleId,
      String referenceId,
      List<String> requestIds,
      String depotId,
      List<OrderLine> items,
      double volume,
      long completedAtTick) {}

  public record CompletedAssignment(
      String vehicleId,
      String referenceId,
      long startedAtTick,
      long completedAtTick,
      double volume,
      String routeType,
      double distanceKm,
      double cost,
      double loadFraction,
      long waitTicks,
      long loadingTicks,
      long unloadingTicks,
      int stopCount,
      double planBenefit,
      double netScore,
      double routeCost,
      double handlingCost,
      double timeCost,
      double extraStopCost,
      double delayPenalty,
      double stockoutPenalty) {}

  public record TickResult(
      String vehicleId,
      List<CompletedStop> completedStops,
      CompletedAssignment completedAssignment) {}

  public record AssignmentSnapshot(
      String vehicleId,
      String referenceId,
      String routeType,
      List<String> plannedNodeIds,
      List<String> remainingNodeIds,
      String activeDepotId,
      int remainingStopCount,
      double loadFraction,
      double totalVolume,
      double capacityVolume,
      double distanceKm,
      long waitTicks,
      long totalLoadingTicks,
      long totalUnloadingTicks,
      long loadingRemainingTicks,
      long unloadingRemainingTicks,
      String currentFromNodeId,
      String currentToNodeId,
      long segmentRemainingTicks,
      long segmentTotalTicks,
      long remainingTotalTicks,
      long startedAtTick,
      long expectedCompletionTick,
      String statusDetail,
      String decisionReason,
      String departureReason,
      String routeSummary,
      String cargoSummary,
      String comparisonSummary,
      double planCost,
      double planBenefit,
      double netScore,
      int plannedStopCount,
      long missionTicks,
      double deliveryDistanceKm,
      double repositionDistanceKm,
      double routeCost,
      double handlingCost,
      double timeCost,
      double extraStopCost,
      double delayPenalty,
      double stockoutPenalty,
      double opportunityCost) {}

  private record Assignment(
      String referenceId,
      ArrayDeque<SegmentPlan> segmentPlans,
      ArrayDeque<StopPlan> stopPlans,
      long startedAtTick,
      double volume,
      String routeType,
      List<String> plannedNodeIds,
      double distanceKm,
      double cost,
      double capacityVolume,
      double loadFraction,
      long waitTicks,
      int totalStopCount,
      long totalLoadingTicks,
      long totalUnloadingTicks,
      long loadingRemainingTicks,
      long unloadingRemainingTicks,
      StopPlan activeStop,
      SegmentProgress activeSegment,
      String decisionReason,
      String departureReason,
      String routeSummary,
      String cargoSummary,
      String comparisonSummary,
      double planCost,
      double planBenefit,
      double netScore,
      long missionTicks,
      double deliveryDistanceKm,
      double repositionDistanceKm,
      double routeCost,
      double handlingCost,
      double timeCost,
      double extraStopCost,
      double delayPenalty,
      double stockoutPenalty,
      double opportunityCost) {

    int stopCount() {
      return stopPlans.size() + (activeStop == null ? 0 : 1);
    }

    Assignment withLoadingRemainingTicks(long value) {
      return new Assignment(referenceId, segmentPlans, stopPlans, startedAtTick, volume, routeType, plannedNodeIds, distanceKm, cost, capacityVolume, loadFraction, waitTicks, totalStopCount, totalLoadingTicks, totalUnloadingTicks, value, unloadingRemainingTicks, activeStop, activeSegment, decisionReason, departureReason, routeSummary, cargoSummary, comparisonSummary, planCost, planBenefit, netScore, missionTicks, deliveryDistanceKm, repositionDistanceKm, routeCost, handlingCost, timeCost, extraStopCost, delayPenalty, stockoutPenalty, opportunityCost);
    }

    Assignment withUnloadingRemainingTicks(long value) {
      return new Assignment(referenceId, segmentPlans, stopPlans, startedAtTick, volume, routeType, plannedNodeIds, distanceKm, cost, capacityVolume, loadFraction, waitTicks, totalStopCount, totalLoadingTicks, totalUnloadingTicks, loadingRemainingTicks, value, activeStop, activeSegment, decisionReason, departureReason, routeSummary, cargoSummary, comparisonSummary, planCost, planBenefit, netScore, missionTicks, deliveryDistanceKm, repositionDistanceKm, routeCost, handlingCost, timeCost, extraStopCost, delayPenalty, stockoutPenalty, opportunityCost);
    }

    Assignment withTotalUnloadingTicks(long value) {
      return new Assignment(referenceId, segmentPlans, stopPlans, startedAtTick, volume, routeType, plannedNodeIds, distanceKm, cost, capacityVolume, loadFraction, waitTicks, totalStopCount, totalLoadingTicks, value, loadingRemainingTicks, unloadingRemainingTicks, activeStop, activeSegment, decisionReason, departureReason, routeSummary, cargoSummary, comparisonSummary, planCost, planBenefit, netScore, missionTicks, deliveryDistanceKm, repositionDistanceKm, routeCost, handlingCost, timeCost, extraStopCost, delayPenalty, stockoutPenalty, opportunityCost);
    }

    Assignment withActiveStop(StopPlan stopPlan) {
      return new Assignment(referenceId, segmentPlans, stopPlans, startedAtTick, volume, routeType, plannedNodeIds, distanceKm, cost, capacityVolume, loadFraction, waitTicks, totalStopCount, totalLoadingTicks, totalUnloadingTicks, loadingRemainingTicks, unloadingRemainingTicks, stopPlan, activeSegment, decisionReason, departureReason, routeSummary, cargoSummary, comparisonSummary, planCost, planBenefit, netScore, missionTicks, deliveryDistanceKm, repositionDistanceKm, routeCost, handlingCost, timeCost, extraStopCost, delayPenalty, stockoutPenalty, opportunityCost);
    }

    Assignment withActiveSegment(SegmentProgress segment) {
      return new Assignment(referenceId, segmentPlans, stopPlans, startedAtTick, volume, routeType, plannedNodeIds, distanceKm, cost, capacityVolume, loadFraction, waitTicks, totalStopCount, totalLoadingTicks, totalUnloadingTicks, loadingRemainingTicks, unloadingRemainingTicks, activeStop, segment, decisionReason, departureReason, routeSummary, cargoSummary, comparisonSummary, planCost, planBenefit, netScore, missionTicks, deliveryDistanceKm, repositionDistanceKm, routeCost, handlingCost, timeCost, extraStopCost, delayPenalty, stockoutPenalty, opportunityCost);
    }

    Assignment withSegmentPlans(ArrayDeque<SegmentPlan> value) {
      return new Assignment(referenceId, value, stopPlans, startedAtTick, volume, routeType, plannedNodeIds, distanceKm, cost, capacityVolume, loadFraction, waitTicks, totalStopCount, totalLoadingTicks, totalUnloadingTicks, loadingRemainingTicks, unloadingRemainingTicks, activeStop, activeSegment, decisionReason, departureReason, routeSummary, cargoSummary, comparisonSummary, planCost, planBenefit, netScore, missionTicks, deliveryDistanceKm, repositionDistanceKm, routeCost, handlingCost, timeCost, extraStopCost, delayPenalty, stockoutPenalty, opportunityCost);
    }

    Assignment withMissionTicks(long value) {
      return new Assignment(referenceId, segmentPlans, stopPlans, startedAtTick, volume, routeType, plannedNodeIds, distanceKm, cost, capacityVolume, loadFraction, waitTicks, totalStopCount, totalLoadingTicks, totalUnloadingTicks, loadingRemainingTicks, unloadingRemainingTicks, activeStop, activeSegment, decisionReason, departureReason, routeSummary, cargoSummary, comparisonSummary, planCost, planBenefit, netScore, value, deliveryDistanceKm, repositionDistanceKm, routeCost, handlingCost, timeCost, extraStopCost, delayPenalty, stockoutPenalty, opportunityCost);
    }
  }

  private record SegmentProgress(
      String fromNodeId,
      String toNodeId,
      double distanceKm,
      double remainingDistanceKm,
      long remainingTicks,
      long totalTicks) {

    SegmentProgress withRemainingTicks(long value) {
      return new SegmentProgress(fromNodeId, toNodeId, distanceKm, remainingDistanceKm, value, totalTicks);
    }

    SegmentProgress withProgress(double nextRemainingDistanceKm, long nextRemainingTicks, long nextTotalTicks) {
      return new SegmentProgress(fromNodeId, toNodeId, distanceKm, nextRemainingDistanceKm, nextRemainingTicks, nextTotalTicks);
    }
  }
}
