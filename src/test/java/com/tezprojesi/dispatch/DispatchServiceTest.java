package com.tezprojesi.dispatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.tezprojesi.domain.Depot;
import com.tezprojesi.domain.DepotType;
import com.tezprojesi.domain.Order;
import com.tezprojesi.domain.Vehicle;
import com.tezprojesi.domain.VehicleStatus;
import com.tezprojesi.events.Event;
import com.tezprojesi.events.EventBus;
import com.tezprojesi.routing.CityGraph;
import com.tezprojesi.routing.RoutingService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DispatchServiceTest {

  private CityGraph graph;
  private RoutingService routingService;

  @BeforeEach
  void setup() {
    graph = new CityGraph();
    routingService = new RoutingService();
    graph.addNode(new com.tezprojesi.domain.Node("DepotA", com.tezprojesi.domain.NodeType.MICRO_DEPOT));
    graph.addNode(new com.tezprojesi.domain.Node("DepotB", com.tezprojesi.domain.NodeType.MICRO_DEPOT));
    graph.addNode(new com.tezprojesi.domain.Node("Customer", com.tezprojesi.domain.NodeType.INTERSECTION));
    graph.addNode(new com.tezprojesi.domain.Node("VehicleA", com.tezprojesi.domain.NodeType.INTERSECTION));

    graph.addEdge(new com.tezprojesi.domain.Edge("DepotA", "Customer", 1.0, 1.0));
    graph.addEdge(new com.tezprojesi.domain.Edge("DepotB", "Customer", 5.0, 1.0));
    graph.addEdge(new com.tezprojesi.domain.Edge("VehicleA", "DepotA", 2.0, 1.0));
  }

  @Test
  void depotSelector_selectsClosestMicroDepotWithStock() {
    DepotSelector selector = new DepotSelector();
    Order order = new Order("order-1", "Customer", 10.0);

    List<Depot> depots = List.of(
        new Depot("d1", DepotType.MICRO, "DepotA"),
        new Depot("d2", DepotType.MICRO, "DepotB")
    );

    Map<String, Double> stock = new HashMap<>();
    stock.put("d1", 20.0);
    stock.put("d2", 20.0);

    Depot result = selector.selectBestMicroDepot(depots, order, stock, graph, routingService);
    assertEquals("d1", result.getId());
  }

  @Test
  void vehicleSelector_selectsClosestAvailableVehicle() {
    VehicleSelector selector = new VehicleSelector();
    Order order = new Order("order-2", "Customer", 5.0);
    Depot depot = new Depot("d1", DepotType.MICRO, "DepotA");

    Vehicle v1 = new Vehicle("v1", 10.0, "VehicleA");
    v1.setStatus(VehicleStatus.IDLE);
    Vehicle v2 = new Vehicle("v2", 100.0, "DepotB");
    v2.setStatus(VehicleStatus.IDLE);

    Vehicle chosen = selector.selectBestVehicle(List.of(v1, v2), order, depot, graph, routingService);
    assertEquals("v1", chosen.getId());
  }

  @Test
  void dispatchService_assignsOrderAndPublishesEvents() {
    DepotSelector depotSelector = new DepotSelector();
    VehicleSelector vehicleSelector = new VehicleSelector();
    EventBus eventBus = new EventBus();
    AtomicInteger pubCount = new AtomicInteger();

    eventBus.subscribe(event -> pubCount.incrementAndGet());

    DispatchService dispatchService = new DispatchService(depotSelector, vehicleSelector, routingService, eventBus);
    Order order = new Order("order-3", "Customer", 5.0);

    List<Depot> depots = List.of(new Depot("d1", DepotType.MICRO, "DepotA"));
    Map<String, Double> stock = new HashMap<>();
    stock.put("d1", 10.0);

    Vehicle vehicle = new Vehicle("v1", 20.0, "VehicleA");
    vehicle.setStatus(VehicleStatus.IDLE);

    DispatchResult result = dispatchService.dispatchOrder(order, depots, stock, List.of(vehicle), graph);

    assertNotNull(result);
    assertEquals("d1", result.getDepotId());
    assertEquals("v1", result.getVehicleId());
    assertEquals(2, pubCount.get());
    assertEquals(VehicleStatus.IDLE, vehicle.getStatus());
    assertEquals("Customer", vehicle.getLocationNodeId());
    assertEquals("v1", order.getAssignedVehicleId());
  }

  @Test
  void dispatchService_throwsWhenNoDepotAvailable() {
    DepotSelector depotSelector = new DepotSelector();
    VehicleSelector vehicleSelector = new VehicleSelector();
    EventBus eventBus = new EventBus();
    DispatchService dispatchService = new DispatchService(depotSelector, vehicleSelector, routingService, eventBus);

    Order order = new Order("order-4", "Customer", 50.0);
    List<Depot> depots = List.of(new Depot("d1", DepotType.MICRO, "DepotA"));
    Map<String, Double> stock = new HashMap<>();
    stock.put("d1", 10.0);
    Vehicle vehicle = new Vehicle("v1", 100.0, "VehicleA");
    vehicle.setStatus(VehicleStatus.IDLE);

    assertThrows(IllegalStateException.class, () -> dispatchService.dispatchOrder(order, depots, stock, List.of(vehicle), graph));
  }
}
