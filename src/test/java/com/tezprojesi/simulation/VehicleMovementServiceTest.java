package com.tezprojesi.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tezprojesi.domain.Edge;
import com.tezprojesi.domain.Node;
import com.tezprojesi.domain.NodeType;
import com.tezprojesi.domain.OrderLine;
import com.tezprojesi.domain.ProductCategory;
import com.tezprojesi.domain.Vehicle;
import com.tezprojesi.domain.VehicleCapability;
import com.tezprojesi.events.EventBus;
import com.tezprojesi.routing.CityGraph;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class VehicleMovementServiceTest {

  @Test
  void getCurrentNodeId_keepsVehicleAnchoredAtFromNodeWhileSegmentIsActive() {
    EventBus eventBus = new EventBus();
    SimulationEventLogService eventLogService = new SimulationEventLogService();
    VehicleMovementService movementService = new VehicleMovementService(eventBus, eventLogService);

    CityGraph graph = new CityGraph();
    graph.addNode(new Node("MAIN", NodeType.MAIN_DEPOT));
    graph.addNode(new Node("M1", NodeType.MICRO_DEPOT));
    graph.addEdge(new Edge("MAIN", "M1", 2.0, 1.0));

    Vehicle vehicle = new Vehicle(
        "V1",
        10.0,
        "MAIN",
        "DIESEL",
        1.0,
        Set.of(ProductCategory.FOOD),
        Set.of(VehicleCapability.STANDARD_BOX),
        "34TEST001",
        "Test Driver",
        0.5,
        2,
        0,
        60.0,
        1000.0,
        1000.0,
        0,
        0);

    movementService.assignRoute(
        vehicle.getId(),
        "assignment-1",
        List.of("MAIN", "M1"),
        List.of(new VehicleMovementService.SegmentPlan("MAIN", "M1", 2.0, 3L)),
        List.of(new VehicleMovementService.StopPlan(
            "M1",
            "M1",
            List.of("REQ-1"),
            List.of(new OrderLine("P1", 2, 1.0)),
            2.0,
            1L)),
        0L,
        2.0,
        "REPLENISHMENT",
        2.0,
        5.0,
        10.0,
        0.2,
        0L,
        0L,
        "Decision",
        "Departure",
        "Route",
        "Cargo",
        "Comparison",
        5.0,
        10.0,
        5.0,
        4L,
        2.0,
        0.0,
        2.0,
        1.0,
        1.0,
        0.0,
        0.0,
        0.0,
        0.0);

    movementService.tick(List.of(vehicle), 1L, graph);

    assertEquals("MAIN", movementService.getCurrentNodeId(vehicle.getId()).orElseThrow());

    VehicleMovementService.AssignmentSnapshot snapshot = movementService.getActiveAssignments().get(0);
    assertEquals("MAIN", snapshot.currentFromNodeId());
    assertEquals("M1", snapshot.currentToNodeId());
    assertTrue(snapshot.segmentTotalTicks() > 0);
  }

  @Test
  void assignmentSnapshot_usesActiveStopNodeWhileFinalUnloadIsInProgress() {
    EventBus eventBus = new EventBus();
    SimulationEventLogService eventLogService = new SimulationEventLogService();
    VehicleMovementService movementService = new VehicleMovementService(eventBus, eventLogService);

    CityGraph graph = new CityGraph();
    graph.addNode(new Node("MAIN", NodeType.MAIN_DEPOT));
    graph.addNode(new Node("M1", NodeType.MICRO_DEPOT));
    graph.addEdge(new Edge("MAIN", "M1", 1.0, 1.0));

    Vehicle vehicle = new Vehicle(
        "V1",
        10.0,
        "MAIN",
        "DIESEL",
        1.0,
        Set.of(ProductCategory.FOOD),
        Set.of(VehicleCapability.STANDARD_BOX),
        "34TEST002",
        "Test Driver",
        0.5,
        2,
        0,
        60.0,
        1000.0,
        1000.0,
        0,
        0);

    movementService.assignRoute(
        vehicle.getId(),
        "assignment-2",
        List.of("MAIN", "M1"),
        List.of(new VehicleMovementService.SegmentPlan("MAIN", "M1", 1.0, 1L)),
        List.of(new VehicleMovementService.StopPlan(
            "M1",
            "M1",
            List.of("REQ-2"),
            List.of(new OrderLine("P1", 2, 1.0)),
            2.0,
            2L)),
        0L,
        2.0,
        "REPLENISHMENT",
        1.0,
        5.0,
        10.0,
        0.2,
        0L,
        0L,
        "Decision",
        "Departure",
        "Route",
        "Cargo",
        "Comparison",
        5.0,
        10.0,
        5.0,
        3L,
        1.0,
        0.0,
        1.0,
        1.0,
        1.0,
        0.0,
        0.0,
        0.0,
        0.0);

    movementService.tick(List.of(vehicle), 1L, graph);
    movementService.tick(List.of(vehicle), 2L, graph);

    VehicleMovementService.AssignmentSnapshot snapshot = movementService.getActiveAssignments().get(0);
    assertEquals("M1", snapshot.currentFromNodeId());
    assertEquals("M1", snapshot.currentToNodeId());
    assertEquals(2L, snapshot.unloadingRemainingTicks());
  }
}
