package com.tezprojesi.simulation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tezprojesi.api.repository.InMemoryOrderRepository;
import com.tezprojesi.dispatch.DispatchService;
import com.tezprojesi.dispatch.DepotSelector;
import com.tezprojesi.dispatch.VehicleSelector;
import com.tezprojesi.domain.Depot;
import com.tezprojesi.domain.DepotType;
import com.tezprojesi.domain.Edge;
import com.tezprojesi.domain.Node;
import com.tezprojesi.domain.NodeType;
import com.tezprojesi.domain.Product;
import com.tezprojesi.domain.ProductCategory;
import com.tezprojesi.domain.ReplenishmentRequest;
import com.tezprojesi.domain.ReplenishmentStatus;
import com.tezprojesi.domain.Vehicle;
import com.tezprojesi.domain.Order;
import com.tezprojesi.domain.OrderLine;
import com.tezprojesi.events.EventBus;
import com.tezprojesi.routing.CityGraph;
import com.tezprojesi.routing.RoutingService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SimulationEngineTest {

  @Test
  void simulationEngine_createsOrdersAndReplenishments() {
    CityGraph graph = new CityGraph();
    graph.addNode(new Node("MAIN", NodeType.MAIN_DEPOT));
    graph.addNode(new Node("M1", NodeType.MICRO_DEPOT));
    graph.addNode(new Node("J1", NodeType.INTERSECTION));
    graph.addNode(new Node("C1", NodeType.CUSTOMER));
    graph.addEdge(new Edge("MAIN", "J1", 1.0, 1.0));
    graph.addEdge(new Edge("J1", "MAIN", 1.0, 1.0));
    graph.addEdge(new Edge("J1", "M1", 1.0, 1.0));
    graph.addEdge(new Edge("M1", "J1", 1.0, 1.0));
    graph.addEdge(new Edge("M1", "C1", 1.0, 1.0));
    graph.addEdge(new Edge("C1", "M1", 1.0, 1.0));

    List<Depot> depots = new ArrayList<>();
    depots.add(new Depot("MAIN", DepotType.MAIN, "MAIN"));
    depots.add(new Depot("M1", DepotType.MICRO, "M1"));

    List<Vehicle> vehicles = new ArrayList<>();
    vehicles.add(new Vehicle("V1", 100.0, "MAIN"));

    EventBus eventBus = new EventBus();
    ProductCatalogService productCatalogService = new ProductCatalogService();
    productCatalogService.save(new Product("P1", "Water", ProductCategory.FOOD, 1.0, 1.0, 100.0));
    InventoryService inventoryService = new InventoryService();
    inventoryService.setStock("MAIN", "P1", 50);
    inventoryService.setStock("M1", "P1", 0);

    OrderGenerator orderGenerator = new OrderGenerator(1.0, List.of("M1"), productCatalogService, new Random(0), eventBus);
    SimulationEventLogService eventLogService = new SimulationEventLogService();
    VehicleMovementService movementService = new VehicleMovementService(eventBus, eventLogService);
    TrafficManager trafficManager = new TrafficManager(new Random(0));
    MetricsEventHandler metrics = new MetricsEventHandler(eventBus);
    ReplenishmentService replenishmentService = new ReplenishmentService();
    VehicleReportService vehicleReportService = new VehicleReportService();
    RoutingService routingService = new RoutingService();
    DispatchService dispatchService = new DispatchService(new DepotSelector(), new VehicleSelector(), routingService, eventBus);

    SimulationEngine engine = new SimulationEngine(
        graph,
        dispatchService,
        routingService,
        orderGenerator,
        movementService,
        trafficManager,
        metrics,
        eventBus,
        productCatalogService,
        inventoryService,
        replenishmentService,
        vehicleReportService,
        eventLogService,
        new InMemoryOrderRepository(),
        new com.tezprojesi.api.repository.InMemoryVehicleRepository(),
        depots,
        vehicles,
        new HashMap<>(Map.of("MAIN", 50.0, "M1", 0.0)),
        new HashMap<>(),
        6,
        0.9,
        1.1,
        0,
        0.5,
        new HashMap<>(),
        750L,
        0.30,
        true);

    engine.run();

    assertFalse(engine.getReplenishments().isEmpty());
    assertTrue(metrics.getTotalOrders() > 0);
    assertTrue(inventoryService.getDepotStock("M1").getOrDefault("P1", 0) >= 0);
  }

  @Test
  void simulationEngine_reservesMicroStockAndCreatesDemandDrivenReplenishmentForBacklog() {
    CityGraph graph = new CityGraph();
    graph.addNode(new Node("MAIN", NodeType.MAIN_DEPOT));
    graph.addNode(new Node("M1", NodeType.MICRO_DEPOT));
    graph.addNode(new Node("J1", NodeType.INTERSECTION));
    graph.addNode(new Node("C1", NodeType.CUSTOMER));
    graph.addEdge(new Edge("MAIN", "J1", 1.0, 1.0));
    graph.addEdge(new Edge("J1", "MAIN", 1.0, 1.0));
    graph.addEdge(new Edge("J1", "M1", 1.0, 1.0));
    graph.addEdge(new Edge("M1", "J1", 1.0, 1.0));
    graph.addEdge(new Edge("M1", "C1", 1.0, 1.0));
    graph.addEdge(new Edge("C1", "M1", 1.0, 1.0));

    List<Depot> depots = new ArrayList<>();
    depots.add(new Depot("MAIN", DepotType.MAIN, "MAIN"));
    depots.add(new Depot("M1", DepotType.MICRO, "M1"));

    EventBus eventBus = new EventBus();
    ProductCatalogService productCatalogService = new ProductCatalogService();
    productCatalogService.save(new Product("P1", "Water", ProductCategory.FOOD, 1.0, 1.0, 100.0));
    InventoryService inventoryService = new InventoryService();
    inventoryService.setStock("MAIN", "P1", 10);
    inventoryService.setStock("M1", "P1", 1);

    OrderGenerator orderGenerator = new OrderGenerator(0.0, List.of("M1"), productCatalogService, new Random(0), eventBus);
    SimulationEventLogService eventLogService = new SimulationEventLogService();
    VehicleMovementService movementService = new VehicleMovementService(eventBus, eventLogService);
    TrafficManager trafficManager = new TrafficManager(new Random(0));
    MetricsEventHandler metrics = new MetricsEventHandler(eventBus);
    ReplenishmentService replenishmentService = new ReplenishmentService();
    VehicleReportService vehicleReportService = new VehicleReportService();
    RoutingService routingService = new RoutingService();
    DispatchService dispatchService = new DispatchService(new DepotSelector(), new VehicleSelector(), routingService, eventBus);

    SimulationEngine engine = new SimulationEngine(
        graph,
        dispatchService,
        routingService,
        orderGenerator,
        movementService,
        trafficManager,
        metrics,
        eventBus,
        productCatalogService,
        inventoryService,
        replenishmentService,
        vehicleReportService,
        eventLogService,
        new InMemoryOrderRepository(),
        new com.tezprojesi.api.repository.InMemoryVehicleRepository(),
        depots,
        List.of(),
        new HashMap<>(Map.of("MAIN", 10.0, "M1", 1.0)),
        new HashMap<>(),
        2,
        0.9,
        1.1,
        0,
        0.5,
        new HashMap<>(),
        750L,
        0.30,
        true);

    Order firstOrder = new Order("manual-1", "C1", List.of(new OrderLine("P1", 1, 1.0)));
    firstOrder.assignMicroDepot("M1");
    Order secondOrder = new Order("manual-2", "C1", List.of(new OrderLine("P1", 2, 1.0)));
    secondOrder.assignMicroDepot("M1");

    engine.submitOrder(firstOrder);
    engine.submitOrder(secondOrder);
    engine.stepTicks(1);

    assertEquals(1, metrics.getCompletedOrders());
    assertEquals(1, engine.getPendingOrderCount());
    assertTrue(engine.getReplenishments().isEmpty());
    assertEquals(0, inventoryService.getReservedStock("MAIN", "P1"));
  }

  @Test
  void simulationEngine_doesNotPlanReturningVehicleBeforeItReachesMainDepot() {
    CityGraph graph = new CityGraph();
    graph.addNode(new Node("MAIN", NodeType.MAIN_DEPOT));
    graph.addNode(new Node("M1", NodeType.MICRO_DEPOT));
    graph.addNode(new Node("REMOTE", NodeType.INTERSECTION));
    graph.addNode(new Node("J1", NodeType.INTERSECTION));
    graph.addEdge(new Edge("MAIN", "J1", 1.0, 1.0));
    graph.addEdge(new Edge("J1", "MAIN", 1.0, 1.0));
    graph.addEdge(new Edge("J1", "M1", 1.0, 1.0));
    graph.addEdge(new Edge("M1", "J1", 1.0, 1.0));
    graph.addEdge(new Edge("REMOTE", "MAIN", 1.0, 1.0));
    graph.addEdge(new Edge("MAIN", "REMOTE", 1.0, 1.0));

    List<Depot> depots = List.of(
        new Depot("MAIN", DepotType.MAIN, "MAIN"),
        new Depot("M1", DepotType.MICRO, "M1"));

    Vehicle vehicle = new Vehicle(
        "V1",
        10.0,
        "REMOTE",
        "DIESEL",
        1.0,
        Set.of(ProductCategory.FOOD),
        Set.of(com.tezprojesi.domain.VehicleCapability.STANDARD_BOX),
        "34V1004",
        "Driver 4",
        0.0,
        2,
        0,
        10.0,
        8.0,
        1,
        1);
    vehicle.setStatus(com.tezprojesi.domain.VehicleStatus.RETURNING);

    EventBus eventBus = new EventBus();
    ProductCatalogService productCatalogService = new ProductCatalogService();
    productCatalogService.save(new Product("P1", "Water", ProductCategory.FOOD, 1.0, 1.0, 100.0));
    InventoryService inventoryService = new InventoryService();
    inventoryService.setStock("MAIN", "P1", 20);
    inventoryService.setStock("M1", "P1", 0);

    OrderGenerator orderGenerator = new OrderGenerator(0.0, List.of("M1"), productCatalogService, new Random(0), eventBus);
    SimulationEventLogService eventLogService = new SimulationEventLogService();
    VehicleMovementService movementService = new VehicleMovementService(eventBus, eventLogService);
    TrafficManager trafficManager = new TrafficManager(new Random(0));
    MetricsEventHandler metrics = new MetricsEventHandler(eventBus);
    ReplenishmentService replenishmentService = new ReplenishmentService();
    VehicleReportService vehicleReportService = new VehicleReportService();
    RoutingService routingService = new RoutingService();
    DispatchService dispatchService = new DispatchService(new DepotSelector(), new VehicleSelector(), routingService, eventBus);

    SimulationEngine engine = new SimulationEngine(
        graph,
        dispatchService,
        routingService,
        orderGenerator,
        movementService,
        trafficManager,
        metrics,
        eventBus,
        productCatalogService,
        inventoryService,
        replenishmentService,
        vehicleReportService,
        eventLogService,
        new InMemoryOrderRepository(),
        new com.tezprojesi.api.repository.InMemoryVehicleRepository(),
        depots,
        List.of(vehicle),
        new HashMap<>(Map.of("MAIN", 20.0, "M1", 0.0)),
        new HashMap<>(),
        5,
        1.0,
        1.01,
        0,
        0.0,
        new HashMap<>(),
        750L,
        0.30,
        true);

    ReplenishmentRequest request = replenishmentService.create("MAIN", "M1", List.of(new OrderLine("P1", 7, 1.0)), 0L);
    engine.submitReplenishmentRequest(request);

    engine.stepTicks(1);

    assertTrue(engine.getVehicleAssignments().isEmpty());
    assertEquals(ReplenishmentStatus.CREATED, request.getStatus());
  }

  @Test
  void simulationEngine_enforcesAtLeastOneTickOfLoading() {
    CityGraph graph = new CityGraph();
    graph.addNode(new Node("MAIN", NodeType.MAIN_DEPOT));
    graph.addNode(new Node("M1", NodeType.MICRO_DEPOT));
    graph.addNode(new Node("J1", NodeType.INTERSECTION));
    graph.addEdge(new Edge("MAIN", "J1", 1.0, 1.0));
    graph.addEdge(new Edge("J1", "MAIN", 1.0, 1.0));
    graph.addEdge(new Edge("J1", "M1", 1.0, 1.0));
    graph.addEdge(new Edge("M1", "J1", 1.0, 1.0));

    List<Depot> depots = List.of(
        new Depot("MAIN", DepotType.MAIN, "MAIN"),
        new Depot("M1", DepotType.MICRO, "M1"));

    Vehicle vehicle = new Vehicle(
        "V1",
        10.0,
        "MAIN",
        "DIESEL",
        1.0,
        Set.of(ProductCategory.FOOD),
        Set.of(com.tezprojesi.domain.VehicleCapability.STANDARD_BOX),
        "34V1005",
        "Driver 5",
        0.0,
        2,
        0,
        1000.0,
        1000.0,
        0,
        0);

    EventBus eventBus = new EventBus();
    ProductCatalogService productCatalogService = new ProductCatalogService();
    productCatalogService.save(new Product("P1", "Water", ProductCategory.FOOD, 1.0, 1.0, 100.0));
    InventoryService inventoryService = new InventoryService();
    inventoryService.setStock("MAIN", "P1", 20);
    inventoryService.setStock("M1", "P1", 0);

    OrderGenerator orderGenerator = new OrderGenerator(0.0, List.of("M1"), productCatalogService, new Random(0), eventBus);
    SimulationEventLogService eventLogService = new SimulationEventLogService();
    VehicleMovementService movementService = new VehicleMovementService(eventBus, eventLogService);
    TrafficManager trafficManager = new TrafficManager(new Random(0));
    MetricsEventHandler metrics = new MetricsEventHandler(eventBus);
    ReplenishmentService replenishmentService = new ReplenishmentService();
    VehicleReportService vehicleReportService = new VehicleReportService();
    RoutingService routingService = new RoutingService();
    DispatchService dispatchService = new DispatchService(new DepotSelector(), new VehicleSelector(), routingService, eventBus);

    SimulationEngine engine = new SimulationEngine(
        graph,
        dispatchService,
        routingService,
        orderGenerator,
        movementService,
        trafficManager,
        metrics,
        eventBus,
        productCatalogService,
        inventoryService,
        replenishmentService,
        vehicleReportService,
        eventLogService,
        new InMemoryOrderRepository(),
        new com.tezprojesi.api.repository.InMemoryVehicleRepository(),
        depots,
        List.of(vehicle),
        new HashMap<>(Map.of("MAIN", 20.0, "M1", 0.0)),
        new HashMap<>(),
        5,
        1.0,
        1.01,
        0,
        0.0,
        new HashMap<>(),
        750L,
        0.30,
        true);

    ReplenishmentRequest request = replenishmentService.create("MAIN", "M1", List.of(new OrderLine("P1", 4, 1.0)), 0L);
    engine.submitReplenishmentRequest(request);

    engine.stepTicks(1);

    assertEquals(com.tezprojesi.domain.VehicleStatus.LOADING, vehicle.getStatus());
    assertEquals(1, engine.getVehicleAssignments().size());
    assertEquals(1L, engine.getVehicleAssignments().get(0).loadingRemainingTicks());
  }

  @Test
  void simulationEngine_doesNotDispatchBelowFiftyPercentEvenWhenMaxWaitIsZero() {
    CityGraph graph = new CityGraph();
    graph.addNode(new Node("MAIN", NodeType.MAIN_DEPOT));
    graph.addNode(new Node("M1", NodeType.MICRO_DEPOT));
    graph.addEdge(new Edge("MAIN", "M1", 5.0, 1.0));
    graph.addEdge(new Edge("M1", "MAIN", 5.0, 1.0));

    List<Depot> depots = List.of(
        new Depot("MAIN", DepotType.MAIN, "MAIN"),
        new Depot("M1", DepotType.MICRO, "M1"));

    Vehicle vehicle = new Vehicle(
        "V1",
        10.0,
        "MAIN",
        "DIESEL",
        1.0,
        Set.of(ProductCategory.FOOD),
        Set.of(com.tezprojesi.domain.VehicleCapability.STANDARD_BOX),
        "34V1006",
        "Driver 6",
        0.5,
        2,
        0,
        35.0,
        1000.0,
        1000.0,
        0,
        0);

    EventBus eventBus = new EventBus();
    ProductCatalogService productCatalogService = new ProductCatalogService();
    productCatalogService.save(new Product("P1", "Water", ProductCategory.FOOD, 1.0, 1.0, 100.0));
    InventoryService inventoryService = new InventoryService();
    inventoryService.setStock("MAIN", "P1", 20);
    inventoryService.setStock("M1", "P1", 0);

    OrderGenerator orderGenerator = new OrderGenerator(0.0, List.of("M1"), productCatalogService, new Random(0), eventBus);
    SimulationEventLogService eventLogService = new SimulationEventLogService();
    VehicleMovementService movementService = new VehicleMovementService(eventBus, eventLogService);
    TrafficManager trafficManager = new TrafficManager(new Random(0));
    trafficManager.setFixedTrafficFactor(graph, "MAIN", "M1", 1.0);
    trafficManager.setFixedTrafficFactor(graph, "M1", "MAIN", 1.0);
    MetricsEventHandler metrics = new MetricsEventHandler(eventBus);
    ReplenishmentService replenishmentService = new ReplenishmentService();
    VehicleReportService vehicleReportService = new VehicleReportService();
    RoutingService routingService = new RoutingService();
    DispatchService dispatchService = new DispatchService(new DepotSelector(), new VehicleSelector(), routingService, eventBus);

    SimulationEngine engine = new SimulationEngine(
        graph,
        dispatchService,
        routingService,
        orderGenerator,
        movementService,
        trafficManager,
        metrics,
        eventBus,
        productCatalogService,
        inventoryService,
        replenishmentService,
        vehicleReportService,
        eventLogService,
        new InMemoryOrderRepository(),
        new com.tezprojesi.api.repository.InMemoryVehicleRepository(),
        depots,
        List.of(vehicle),
        new HashMap<>(Map.of("MAIN", 20.0, "M1", 0.0)),
        new HashMap<>(),
        5,
        1.0,
        1.01,
        0,
        0.5,
        new HashMap<>(),
        750L,
        0.30,
        true);

    ReplenishmentRequest request = replenishmentService.create("MAIN", "M1", List.of(new OrderLine("P1", 3, 1.0)), 0L);
    engine.submitReplenishmentRequest(request);

    engine.stepTicks(1);

    assertTrue(engine.getVehicleAssignments().isEmpty());
    assertEquals(ReplenishmentStatus.CREATED, request.getStatus());
  }

  @Test
  void simulationEngine_usesVehicleSpeedForTravelTimeCalculation() {
    CityGraph graph = new CityGraph();
    graph.addNode(new Node("MAIN", NodeType.MAIN_DEPOT));
    graph.addNode(new Node("M1", NodeType.MICRO_DEPOT));
    graph.addEdge(new Edge("MAIN", "M1", 10.0, 1.0));
    graph.addEdge(new Edge("M1", "MAIN", 10.0, 1.0));

    List<Depot> depots = List.of(
        new Depot("MAIN", DepotType.MAIN, "MAIN"),
        new Depot("M1", DepotType.MICRO, "M1"));

    Vehicle vehicle = new Vehicle(
        "V1",
        10.0,
        "MAIN",
        "DIESEL",
        1.0,
        Set.of(ProductCategory.FOOD),
        Set.of(com.tezprojesi.domain.VehicleCapability.STANDARD_BOX),
        "34V1007",
        "Driver 7",
        0.5,
        2,
        0,
        30.0,
        1000.0,
        1000.0,
        0,
        0);

    EventBus eventBus = new EventBus();
    ProductCatalogService productCatalogService = new ProductCatalogService();
    productCatalogService.save(new Product("P1", "Water", ProductCategory.FOOD, 1.0, 1.0, 100.0));
    InventoryService inventoryService = new InventoryService();
    inventoryService.setStock("MAIN", "P1", 20);
    inventoryService.setStock("M1", "P1", 0);

    OrderGenerator orderGenerator = new OrderGenerator(0.0, List.of("M1"), productCatalogService, new Random(0), eventBus);
    SimulationEventLogService eventLogService = new SimulationEventLogService();
    VehicleMovementService movementService = new VehicleMovementService(eventBus, eventLogService);
    TrafficManager trafficManager = new TrafficManager(new Random(0));
    MetricsEventHandler metrics = new MetricsEventHandler(eventBus);
    ReplenishmentService replenishmentService = new ReplenishmentService();
    VehicleReportService vehicleReportService = new VehicleReportService();
    RoutingService routingService = new RoutingService();
    DispatchService dispatchService = new DispatchService(new DepotSelector(), new VehicleSelector(), routingService, eventBus);

    SimulationEngine engine = new SimulationEngine(
        graph,
        dispatchService,
        routingService,
        orderGenerator,
        movementService,
        trafficManager,
        metrics,
        eventBus,
        productCatalogService,
        inventoryService,
        replenishmentService,
        vehicleReportService,
        eventLogService,
        new InMemoryOrderRepository(),
        new com.tezprojesi.api.repository.InMemoryVehicleRepository(),
        depots,
        List.of(vehicle),
        new HashMap<>(Map.of("MAIN", 20.0, "M1", 0.0)),
        new HashMap<>(),
        5,
        1.0,
        1.01,
        0,
        0.5,
        new HashMap<>(),
        750L,
        0.30,
        true);

    ReplenishmentRequest request = replenishmentService.create("MAIN", "M1", List.of(new OrderLine("P1", 7, 1.0)), 0L);
    engine.submitReplenishmentRequest(request);

    engine.stepTicks(1);

    assertEquals(1, engine.getVehicleAssignments().size());
    assertEquals(1L, engine.getVehicleAssignments().get(0).loadingRemainingTicks());
    assertEquals(23L, engine.getVehicleAssignments().get(0).remainingTotalTicks());
  }

  @Test
  void simulationEngine_doesNotDuplicateDemandDrivenReplenishmentWhileCooldownActive() {
    CityGraph graph = new CityGraph();
    graph.addNode(new Node("MAIN", NodeType.MAIN_DEPOT));
    graph.addNode(new Node("M1", NodeType.MICRO_DEPOT));
    graph.addNode(new Node("C1", NodeType.CUSTOMER));
    graph.addEdge(new Edge("MAIN", "M1", 5.0, 1.0));
    graph.addEdge(new Edge("M1", "MAIN", 5.0, 1.0));
    graph.addEdge(new Edge("M1", "C1", 1.0, 1.0));
    graph.addEdge(new Edge("C1", "M1", 1.0, 1.0));

    List<Depot> depots = List.of(
        new Depot("MAIN", DepotType.MAIN, "MAIN"),
        new Depot("M1", DepotType.MICRO, "M1"));

    EventBus eventBus = new EventBus();
    ProductCatalogService productCatalogService = new ProductCatalogService();
    productCatalogService.save(new Product("P1", "Water", ProductCategory.FOOD, 1.0, 1.0, 100.0));
    InventoryService inventoryService = new InventoryService();
    inventoryService.setStock("MAIN", "P1", 20);
    inventoryService.setStock("M1", "P1", 4);

    OrderGenerator orderGenerator = new OrderGenerator(0.0, List.of("M1"), productCatalogService, new Random(0), eventBus);
    SimulationEventLogService eventLogService = new SimulationEventLogService();
    VehicleMovementService movementService = new VehicleMovementService(eventBus, eventLogService);
    TrafficManager trafficManager = new TrafficManager(new Random(0));
    MetricsEventHandler metrics = new MetricsEventHandler(eventBus);
    ReplenishmentService replenishmentService = new ReplenishmentService();
    VehicleReportService vehicleReportService = new VehicleReportService();
    RoutingService routingService = new RoutingService();
    DispatchService dispatchService = new DispatchService(new DepotSelector(), new VehicleSelector(), routingService, eventBus);

    SimulationEngine engine = new SimulationEngine(
        graph,
        dispatchService,
        routingService,
        orderGenerator,
        movementService,
        trafficManager,
        metrics,
        eventBus,
        productCatalogService,
        inventoryService,
        replenishmentService,
        vehicleReportService,
        eventLogService,
        new InMemoryOrderRepository(),
        new com.tezprojesi.api.repository.InMemoryVehicleRepository(),
        depots,
        List.of(new Vehicle("V1", 10.0, "MAIN")),
        new HashMap<>(Map.of("MAIN", 20.0, "M1", 4.0)),
        new HashMap<>(),
        20,
        1.0,
        1.01,
        0,
        0.5,
        new HashMap<>(),
        750L,
        0.30,
        true);

    Order order = new Order("manual-3", "C1", List.of(new OrderLine("P1", 3, 1.0)));
    order.assignMicroDepot("M1");
    engine.submitOrder(order);
    engine.stepTicks(1);

    assertEquals(1, engine.getReplenishments().size());
    engine.stepTicks(1);
    assertEquals(1, engine.getReplenishments().size());
  }

  @Test
  void simulationEngine_allowsAdditionalDemandDrivenReplenishmentWhenExistingRequestsDoNotCoverTheFullGap() {
    CityGraph graph = new CityGraph();
    graph.addNode(new Node("MAIN", NodeType.MAIN_DEPOT));
    graph.addNode(new Node("M1", NodeType.MICRO_DEPOT));
    graph.addNode(new Node("J1", NodeType.INTERSECTION));
    graph.addNode(new Node("C1", NodeType.CUSTOMER));
    graph.addEdge(new Edge("MAIN", "J1", 1.0, 1.0));
    graph.addEdge(new Edge("J1", "MAIN", 1.0, 1.0));
    graph.addEdge(new Edge("J1", "M1", 1.0, 1.0));
    graph.addEdge(new Edge("M1", "J1", 1.0, 1.0));

    List<Depot> depots = List.of(
        new Depot("MAIN", DepotType.MAIN, "MAIN"),
        new Depot("M1", DepotType.MICRO, "M1"));

    EventBus eventBus = new EventBus();
    ProductCatalogService productCatalogService = new ProductCatalogService();
    productCatalogService.save(new Product("P1", "Water", ProductCategory.FOOD, 1.0, 1.0, 100.0));
    InventoryService inventoryService = new InventoryService();
    inventoryService.setStock("MAIN", "P1", 20);
    inventoryService.setStock("M1", "P1", 0);

    OrderGenerator orderGenerator = new OrderGenerator(0.0, List.of("M1"), productCatalogService, new Random(0), eventBus);
    SimulationEventLogService eventLogService = new SimulationEventLogService();
    VehicleMovementService movementService = new VehicleMovementService(eventBus, eventLogService);
    TrafficManager trafficManager = new TrafficManager(new Random(0));
    MetricsEventHandler metrics = new MetricsEventHandler(eventBus);
    ReplenishmentService replenishmentService = new ReplenishmentService();
    VehicleReportService vehicleReportService = new VehicleReportService();
    RoutingService routingService = new RoutingService();
    DispatchService dispatchService = new DispatchService(new DepotSelector(), new VehicleSelector(), routingService, eventBus);

    SimulationEngine engine = new SimulationEngine(
        graph,
        dispatchService,
        routingService,
        orderGenerator,
        movementService,
        trafficManager,
        metrics,
        eventBus,
        productCatalogService,
        inventoryService,
        replenishmentService,
        vehicleReportService,
        eventLogService,
        new InMemoryOrderRepository(),
        new com.tezprojesi.api.repository.InMemoryVehicleRepository(),
        depots,
        List.of(new Vehicle("V1", 2.0, "MAIN")),
        new HashMap<>(Map.of("MAIN", 20.0, "M1", 0.0)),
        new HashMap<>(),
        20,
        1.0,
        1.01,
        0,
        0.5,
        new HashMap<>(),
        750L,
        0.30,
        true);

    Order order = new Order("manual-2", "C1", List.of(new OrderLine("P1", 5, 1.0)));
    order.assignMicroDepot("M1");
    engine.submitOrder(order);

    engine.stepTicks(1);
    assertEquals(1, engine.getReplenishments().size());

    engine.stepTicks(1);
    assertEquals(2, engine.getReplenishments().size());
  }

  @Test
  void simulationEngine_doesNotAssignSameReplenishmentToMultipleVehicles() {
    CityGraph graph = new CityGraph();
    graph.addNode(new Node("MAIN", NodeType.MAIN_DEPOT));
    graph.addNode(new Node("M1", NodeType.MICRO_DEPOT));
    graph.addNode(new Node("REMOTE", NodeType.INTERSECTION));
    graph.addNode(new Node("J1", NodeType.INTERSECTION));
    graph.addEdge(new Edge("REMOTE", "MAIN", 1.0, 1.0));
    graph.addEdge(new Edge("MAIN", "REMOTE", 1.0, 1.0));
    graph.addEdge(new Edge("MAIN", "J1", 1.0, 1.0));
    graph.addEdge(new Edge("J1", "MAIN", 1.0, 1.0));
    graph.addEdge(new Edge("J1", "M1", 1.0, 1.0));
    graph.addEdge(new Edge("M1", "J1", 1.0, 1.0));

    List<Depot> depots = List.of(
        new Depot("MAIN", DepotType.MAIN, "MAIN"),
        new Depot("M1", DepotType.MICRO, "M1"));

    Vehicle repositioningVehicle = new Vehicle(
        "V1",
        10.0,
        "REMOTE",
        "DIESEL",
        1.0,
        Set.of(ProductCategory.FOOD),
        Set.of(com.tezprojesi.domain.VehicleCapability.STANDARD_BOX),
        "34V1001",
        "Driver 1",
        0.5,
        2,
        0,
        10.0,
        8.0,
        1,
        1);
    Vehicle standbyVehicle = new Vehicle(
        "V2",
        10.0,
        "MAIN",
        "DIESEL",
        1.0,
        Set.of(ProductCategory.FOOD),
        Set.of(com.tezprojesi.domain.VehicleCapability.STANDARD_BOX),
        "34V1002",
        "Driver 2",
        0.5,
        2,
        0,
        10.0,
        8.0,
        1,
        1);

    EventBus eventBus = new EventBus();
    ProductCatalogService productCatalogService = new ProductCatalogService();
    productCatalogService.save(new Product("P1", "Water", ProductCategory.FOOD, 1.0, 1.0, 100.0));
    InventoryService inventoryService = new InventoryService();
    inventoryService.setStock("MAIN", "P1", 10);
    inventoryService.setStock("M1", "P1", 0);

    OrderGenerator orderGenerator = new OrderGenerator(0.0, List.of("M1"), productCatalogService, new Random(0), eventBus);
    SimulationEventLogService eventLogService = new SimulationEventLogService();
    VehicleMovementService movementService = new VehicleMovementService(eventBus, eventLogService);
    TrafficManager trafficManager = new TrafficManager(new Random(0));
    MetricsEventHandler metrics = new MetricsEventHandler(eventBus);
    ReplenishmentService replenishmentService = new ReplenishmentService();
    VehicleReportService vehicleReportService = new VehicleReportService();
    RoutingService routingService = new RoutingService();
    DispatchService dispatchService = new DispatchService(new DepotSelector(), new VehicleSelector(), routingService, eventBus);

    SimulationEngine engine = new SimulationEngine(
        graph,
        dispatchService,
        routingService,
        orderGenerator,
        movementService,
        trafficManager,
        metrics,
        eventBus,
        productCatalogService,
        inventoryService,
        replenishmentService,
        vehicleReportService,
        eventLogService,
        new InMemoryOrderRepository(),
        new com.tezprojesi.api.repository.InMemoryVehicleRepository(),
        depots,
        List.of(repositioningVehicle, standbyVehicle),
        new HashMap<>(Map.of("MAIN", 10.0, "M1", 0.0)),
        new HashMap<>(),
        10,
        1.0,
        1.01,
        0,
        0.5,
        new HashMap<>(),
        750L,
        0.30,
        true);

    ReplenishmentRequest request = replenishmentService.create("MAIN", "M1", List.of(new OrderLine("P1", 7, 1.0)), 0L);
    engine.submitReplenishmentRequest(request);

    engine.stepTicks(1);

    assertEquals(1, engine.getVehicleAssignments().size());
    assertEquals("V1", request.getAssignedVehicleId());
    assertEquals(com.tezprojesi.domain.ReplenishmentStatus.ASSIGNED, request.getStatus());
  }

  @Test
  void simulationEngine_waitsForManualApprovalBeforeDispatchingReadyVehicle() {
    CityGraph graph = new CityGraph();
    graph.addNode(new Node("MAIN", NodeType.MAIN_DEPOT));
    graph.addNode(new Node("M1", NodeType.MICRO_DEPOT));
    graph.addNode(new Node("J1", NodeType.INTERSECTION));
    graph.addEdge(new Edge("MAIN", "J1", 1.0, 1.0));
    graph.addEdge(new Edge("J1", "MAIN", 1.0, 1.0));
    graph.addEdge(new Edge("J1", "M1", 1.0, 1.0));
    graph.addEdge(new Edge("M1", "J1", 1.0, 1.0));

    List<Depot> depots = List.of(
        new Depot("MAIN", DepotType.MAIN, "MAIN"),
        new Depot("M1", DepotType.MICRO, "M1"));

    Vehicle vehicle = new Vehicle(
        "V1",
        10.0,
        "MAIN",
        "DIESEL",
        1.0,
        Set.of(ProductCategory.FOOD),
        Set.of(com.tezprojesi.domain.VehicleCapability.STANDARD_BOX),
        "34V1003",
        "Driver 3",
        0.5,
        2,
        10,
        10.0,
        8.0,
        1,
        1);

    EventBus eventBus = new EventBus();
    ProductCatalogService productCatalogService = new ProductCatalogService();
    productCatalogService.save(new Product("P1", "Water", ProductCategory.FOOD, 1.0, 1.0, 100.0));
    InventoryService inventoryService = new InventoryService();
    inventoryService.setStock("MAIN", "P1", 20);
    inventoryService.setStock("M1", "P1", 0);

    OrderGenerator orderGenerator = new OrderGenerator(0.0, List.of("M1"), productCatalogService, new Random(0), eventBus);
    SimulationEventLogService eventLogService = new SimulationEventLogService();
    VehicleMovementService movementService = new VehicleMovementService(eventBus, eventLogService);
    TrafficManager trafficManager = new TrafficManager(new Random(0));
    MetricsEventHandler metrics = new MetricsEventHandler(eventBus);
    ReplenishmentService replenishmentService = new ReplenishmentService();
    VehicleReportService vehicleReportService = new VehicleReportService();
    RoutingService routingService = new RoutingService();
    DispatchService dispatchService = new DispatchService(new DepotSelector(), new VehicleSelector(), routingService, eventBus);

    SimulationEngine engine = new SimulationEngine(
        graph,
        dispatchService,
        routingService,
        orderGenerator,
        movementService,
        trafficManager,
        metrics,
        eventBus,
        productCatalogService,
        inventoryService,
        replenishmentService,
        vehicleReportService,
        eventLogService,
        new InMemoryOrderRepository(),
        new com.tezprojesi.api.repository.InMemoryVehicleRepository(),
        depots,
        List.of(vehicle),
        new HashMap<>(Map.of("MAIN", 20.0, "M1", 0.0)),
        new HashMap<>(),
        20,
        1.0,
        1.01,
        0,
        0.5,
        new HashMap<>(),
        750L,
        0.30,
        true);

    ReplenishmentRequest request = replenishmentService.create("MAIN", "M1", List.of(new OrderLine("P1", 7, 1.0)), 0L);
    engine.submitReplenishmentRequest(request);

    engine.stepTicks(1);

    assertTrue(engine.getVehicleAssignments().isEmpty());
    assertEquals(1, engine.getDispatchReadiness().size());
    assertTrue(engine.getDispatchReadiness().get(0).awaitingApproval());
    assertEquals(ReplenishmentStatus.ASSIGNED, request.getStatus());

    engine.approveReadyDispatch("V1");

    assertEquals(1, engine.getVehicleAssignments().size());
    assertEquals(ReplenishmentStatus.IN_TRANSIT, request.getStatus());
  }

  @Test
  void simulationEngine_exposesSegmentProgressForMapRendering() {
    CityGraph graph = new CityGraph();
    graph.addNode(new Node("MAIN", NodeType.MAIN_DEPOT));
    graph.addNode(new Node("M1", NodeType.MICRO_DEPOT));
    graph.addEdge(new Edge("MAIN", "M1", 2.0, 1.0));
    graph.addEdge(new Edge("M1", "MAIN", 2.0, 1.0));

    List<Depot> depots = List.of(
        new Depot("MAIN", DepotType.MAIN, "MAIN"),
        new Depot("M1", DepotType.MICRO, "M1"));

    Vehicle vehicle = new Vehicle(
        "V1",
        10.0,
        "MAIN",
        "DIESEL",
        1.0,
        Set.of(ProductCategory.FOOD),
        Set.of(com.tezprojesi.domain.VehicleCapability.STANDARD_BOX),
        "34V1008",
        "Driver 8",
        0.5,
        2,
        0,
        60.0,
        1000.0,
        1000.0,
        0,
        0);

    EventBus eventBus = new EventBus();
    ProductCatalogService productCatalogService = new ProductCatalogService();
    productCatalogService.save(new Product("P1", "Water", ProductCategory.FOOD, 1.0, 1.0, 100.0));
    InventoryService inventoryService = new InventoryService();
    inventoryService.setStock("MAIN", "P1", 20);
    inventoryService.setStock("M1", "P1", 0);

    OrderGenerator orderGenerator = new OrderGenerator(0.0, List.of("M1"), productCatalogService, new Random(0), eventBus);
    SimulationEventLogService eventLogService = new SimulationEventLogService();
    VehicleMovementService movementService = new VehicleMovementService(eventBus, eventLogService);
    TrafficManager trafficManager = new TrafficManager(new Random(0));
    trafficManager.setFixedTrafficFactor(graph, "MAIN", "M1", 1.0);
    trafficManager.setFixedTrafficFactor(graph, "M1", "MAIN", 1.0);
    MetricsEventHandler metrics = new MetricsEventHandler(eventBus);
    ReplenishmentService replenishmentService = new ReplenishmentService();
    VehicleReportService vehicleReportService = new VehicleReportService();
    RoutingService routingService = new RoutingService();
    DispatchService dispatchService = new DispatchService(new DepotSelector(), new VehicleSelector(), routingService, eventBus);

    SimulationEngine engine = new SimulationEngine(
        graph,
        dispatchService,
        routingService,
        orderGenerator,
        movementService,
        trafficManager,
        metrics,
        eventBus,
        productCatalogService,
        inventoryService,
        replenishmentService,
        vehicleReportService,
        eventLogService,
        new InMemoryOrderRepository(),
        new com.tezprojesi.api.repository.InMemoryVehicleRepository(),
        depots,
        List.of(vehicle),
        new HashMap<>(Map.of("MAIN", 20.0, "M1", 0.0)),
        new HashMap<>(),
        8,
        1.0,
        1.01,
        0,
        0.5,
        new HashMap<>(),
        750L,
        0.30,
        true);

    ReplenishmentRequest request = replenishmentService.create("MAIN", "M1", List.of(new OrderLine("P1", 7, 1.0)), 0L);
    engine.submitReplenishmentRequest(request);

    engine.stepTicks(3);

    assertEquals(1, engine.getVehicleAssignments().size());
    var assignment = engine.getVehicleAssignments().get(0);
    assertEquals("MAIN", assignment.currentFromNodeId());
    assertEquals("M1", assignment.currentToNodeId());
    assertTrue(assignment.segmentTotalTicks() > 0);

    engine.stepTicks(1);

    assertEquals("M1", vehicle.getLocationNodeId());
  }

  @Test
  void simulationEngine_updatesTravelTimeWhenTrafficChangesButKeepsCurrentRoute() {
    CityGraph graph = new CityGraph();
    graph.addNode(new Node("MAIN", NodeType.MAIN_DEPOT));
    graph.addNode(new Node("A", NodeType.INTERSECTION));
    graph.addNode(new Node("B", NodeType.INTERSECTION));
    graph.addNode(new Node("M1", NodeType.MICRO_DEPOT));
    graph.addEdge(new Edge("MAIN", "A", 1.0, 1.0));
    graph.addEdge(new Edge("A", "MAIN", 1.0, 1.0));
    graph.addEdge(new Edge("A", "M1", 1.0, 1.0));
    graph.addEdge(new Edge("M1", "A", 1.0, 1.0));
    graph.addEdge(new Edge("MAIN", "B", 1.0, 1.0));
    graph.addEdge(new Edge("B", "MAIN", 1.0, 1.0));
    graph.addEdge(new Edge("B", "M1", 3.0, 1.0));
    graph.addEdge(new Edge("M1", "B", 3.0, 1.0));

    List<Depot> depots = List.of(
        new Depot("MAIN", DepotType.MAIN, "MAIN"),
        new Depot("M1", DepotType.MICRO, "M1"));

    Vehicle vehicle = new Vehicle(
        "V1",
        10.0,
        "MAIN",
        "DIESEL",
        1.0,
        Set.of(ProductCategory.FOOD),
        Set.of(com.tezprojesi.domain.VehicleCapability.STANDARD_BOX),
        "34V1009",
        "Driver 9",
        0.5,
        2,
        0,
        60.0,
        1000.0,
        1000.0,
        0,
        0);

    EventBus eventBus = new EventBus();
    ProductCatalogService productCatalogService = new ProductCatalogService();
    productCatalogService.save(new Product("P1", "Water", ProductCategory.FOOD, 1.0, 1.0, 100.0));
    InventoryService inventoryService = new InventoryService();
    inventoryService.setStock("MAIN", "P1", 20);
    inventoryService.setStock("M1", "P1", 0);

    OrderGenerator orderGenerator = new OrderGenerator(0.0, List.of("M1"), productCatalogService, new Random(0), eventBus);
    SimulationEventLogService eventLogService = new SimulationEventLogService();
    VehicleMovementService movementService = new VehicleMovementService(eventBus, eventLogService);
    TrafficManager trafficManager = new TrafficManager(new Random(0));
    trafficManager.setFixedTrafficFactor(graph, "MAIN", "A", 1.0);
    trafficManager.setFixedTrafficFactor(graph, "A", "MAIN", 1.0);
    trafficManager.setFixedTrafficFactor(graph, "A", "M1", 1.0);
    trafficManager.setFixedTrafficFactor(graph, "M1", "A", 1.0);
    trafficManager.setFixedTrafficFactor(graph, "MAIN", "B", 1.0);
    trafficManager.setFixedTrafficFactor(graph, "B", "MAIN", 1.0);
    trafficManager.setFixedTrafficFactor(graph, "B", "M1", 1.0);
    trafficManager.setFixedTrafficFactor(graph, "M1", "B", 1.0);
    MetricsEventHandler metrics = new MetricsEventHandler(eventBus);
    ReplenishmentService replenishmentService = new ReplenishmentService();
    VehicleReportService vehicleReportService = new VehicleReportService();
    RoutingService routingService = new RoutingService();
    DispatchService dispatchService = new DispatchService(new DepotSelector(), new VehicleSelector(), routingService, eventBus);

    SimulationEngine engine = new SimulationEngine(
        graph,
        dispatchService,
        routingService,
        orderGenerator,
        movementService,
        trafficManager,
        metrics,
        eventBus,
        productCatalogService,
        inventoryService,
        replenishmentService,
        vehicleReportService,
        eventLogService,
        new InMemoryOrderRepository(),
        new com.tezprojesi.api.repository.InMemoryVehicleRepository(),
        depots,
        List.of(vehicle),
        new HashMap<>(Map.of("MAIN", 20.0, "M1", 0.0)),
        new HashMap<>(),
        8,
        1.0,
        1.01,
        0,
        0.5,
        new HashMap<>(),
        750L,
        0.30,
        true);

    ReplenishmentRequest request = replenishmentService.create("MAIN", "M1", List.of(new OrderLine("P1", 7, 1.0)), 0L);
    engine.submitReplenishmentRequest(request);

    engine.stepTicks(2);
    var initialAssignment = engine.getVehicleAssignments().get(0);
    List<String> initialRoute = initialAssignment.plannedNodeIds();
    long initialRemaining = initialAssignment.remainingTotalTicks();

    trafficManager.setFixedTrafficFactor(graph, "MAIN", "A", 4.0);
    trafficManager.setFixedTrafficFactor(graph, "A", "M1", 4.0);
    trafficManager.setFixedTrafficFactor(graph, "MAIN", "B", 1.0);
    trafficManager.setFixedTrafficFactor(graph, "B", "M1", 1.0);

    engine.stepTicks(1);

    var updatedAssignment = engine.getVehicleAssignments().get(0);
    assertEquals(initialRoute, updatedAssignment.plannedNodeIds());
    assertTrue(updatedAssignment.remainingTotalTicks() > initialRemaining);
    assertTrue(updatedAssignment.plannedNodeIds().contains("A"));
    assertFalse(updatedAssignment.plannedNodeIds().contains("B"));
  }

  @Test
  void simulationEngine_escalatesToManualApprovalWhenSlaWaitThresholdExceededEvenUnderFiftyPercentLoad() {
    CityGraph graph = new CityGraph();
    graph.addNode(new Node("MAIN", NodeType.MAIN_DEPOT));
    graph.addNode(new Node("M1", NodeType.MICRO_DEPOT));
    graph.addEdge(new Edge("MAIN", "M1", 5.0, 1.0));
    graph.addEdge(new Edge("M1", "MAIN", 5.0, 1.0));

    List<Depot> depots = List.of(
        new Depot("MAIN", DepotType.MAIN, "MAIN"),
        new Depot("M1", DepotType.MICRO, "M1"));

    Vehicle vehicle = new Vehicle(
        "V1",
        10.0,
        "MAIN",
        "DIESEL",
        1.0,
        Set.of(ProductCategory.FOOD),
        Set.of(com.tezprojesi.domain.VehicleCapability.STANDARD_BOX),
        "34SLA001",
        "Driver SLA",
        0.5, // 50% min load
        2,
        0, // maxWaitTicks
        35.0,
        1000.0,
        1000.0,
        0,
        0);

    EventBus eventBus = new EventBus();
    ProductCatalogService productCatalogService = new ProductCatalogService();
    productCatalogService.save(new Product("P1", "Water", ProductCategory.FOOD, 1.0, 1.0, 100.0));
    InventoryService inventoryService = new InventoryService();
    inventoryService.setStock("MAIN", "P1", 20);
    inventoryService.setStock("M1", "P1", 0);

    OrderGenerator orderGenerator = new OrderGenerator(0.0, List.of("M1"), productCatalogService, new Random(0), eventBus);
    SimulationEventLogService eventLogService = new SimulationEventLogService();
    VehicleMovementService movementService = new VehicleMovementService(eventBus, eventLogService);
    TrafficManager trafficManager = new TrafficManager(new Random(0));
    MetricsEventHandler metrics = new MetricsEventHandler(eventBus);
    ReplenishmentService replenishmentService = new ReplenishmentService();
    VehicleReportService vehicleReportService = new VehicleReportService();
    RoutingService routingService = new RoutingService();
    DispatchService dispatchService = new DispatchService(new DepotSelector(), new VehicleSelector(), routingService, eventBus);

    SimulationEngine engine = new SimulationEngine(
        graph,
        dispatchService,
        routingService,
        orderGenerator,
        movementService,
        trafficManager,
        metrics,
        eventBus,
        productCatalogService,
        inventoryService,
        replenishmentService,
        vehicleReportService,
        eventLogService,
        new InMemoryOrderRepository(),
        new com.tezprojesi.api.repository.InMemoryVehicleRepository(),
        depots,
        List.of(vehicle),
        new HashMap<>(Map.of("MAIN", 20.0, "M1", 0.0)),
        new HashMap<>(),
        1000,
        1.0,
        1.01,
        0,
        0.5,
        new HashMap<>(),
        750L,
        0.30,
        true);

    // Request is only 30% load (3.0 out of 10.0), created at tick 0
    ReplenishmentRequest request = replenishmentService.create("MAIN", "M1", List.of(new OrderLine("P1", 3, 1.0)), 0L);
    engine.submitReplenishmentRequest(request);

    // At tick 1: load is under 50% and wait time is only 1 tick (< 300 Grace Period). Must NOT dispatch and NOT await approval.
    engine.stepTicks(1);
    assertTrue(engine.getVehicleAssignments().isEmpty());
    assertTrue(engine.getDispatchReadiness().isEmpty() || !engine.getDispatchReadiness().get(0).awaitingApproval());

    // Advance 200 ticks -> tick 201 (still inside 300-tick grace period). Must STILL wait for consolidation.
    engine.stepTicks(200);
    assertTrue(engine.getVehicleAssignments().isEmpty());
    assertTrue(engine.getDispatchReadiness().isEmpty() || !engine.getDispatchReadiness().get(0).awaitingApproval());

    // Advance to tick 480 (overdue = 180 ticks, agingScore = 180^2 * 0.02 = 648.0 >= 600.0). SLA wait threshold exceeded!
    engine.stepTicks(279);

    // Verify it is now escalated for human operator approval even under 50% load!
    assertFalse(engine.getDispatchReadiness().isEmpty());
    assertTrue(engine.getDispatchReadiness().get(0).awaitingApproval(), "Order exceeded 5-hour grace period and aging threshold (>=600), must escalate for human approval");
    assertTrue(engine.getVehicleAssignments().isEmpty(), "Vehicle must not depart autonomously under 50% load without operator confirmation");

    // Operator approves the early departure
    engine.approveReadyDispatch("V1");

    // Vehicle is now dispatched!
    assertEquals(1, engine.getVehicleAssignments().size());
    assertEquals(ReplenishmentStatus.IN_TRANSIT, request.getStatus());
  }

  @Test
  void simulationEngine_criticalLowLoad_triggersFinancialWarningAndForceDispatch() {
    CityGraph graph = new CityGraph();
    graph.addNode(new Node("MAIN", NodeType.MAIN_DEPOT));
    graph.addNode(new Node("M1", NodeType.MICRO_DEPOT));
    graph.addEdge(new Edge("MAIN", "M1", 5.0, 1.0));
    graph.addEdge(new Edge("M1", "MAIN", 5.0, 1.0));

    List<Depot> depots = List.of(
        new Depot("MAIN", DepotType.MAIN, "MAIN"),
        new Depot("M1", DepotType.MICRO, "M1"));

    Vehicle vehicle = new Vehicle(
        "V1",
        10.0,
        "MAIN",
        "DIESEL",
        1.0,
        Set.of(ProductCategory.FOOD),
        Set.of(com.tezprojesi.domain.VehicleCapability.STANDARD_BOX),
        "34LOW001",
        "Driver LOW",
        0.5,
        2,
        0,
        35.0,
        1000.0,
        1000.0,
        0,
        0);

    EventBus eventBus = new EventBus();
    ProductCatalogService productCatalogService = new ProductCatalogService();
    productCatalogService.save(new Product("P1", "Water", ProductCategory.FOOD, 0.5, 1.0, 20.0));
    InventoryService inventoryService = new InventoryService();
    inventoryService.setStock("MAIN", "P1", 20);
    inventoryService.setStock("M1", "P1", 0);

    OrderGenerator orderGenerator = new OrderGenerator(0.0, List.of("M1"), productCatalogService, new Random(0), eventBus);
    SimulationEventLogService eventLogService = new SimulationEventLogService();
    VehicleMovementService movementService = new VehicleMovementService(eventBus, eventLogService);
    TrafficManager trafficManager = new TrafficManager(new Random(0));
    MetricsEventHandler metrics = new MetricsEventHandler(eventBus);
    ReplenishmentService replenishmentService = new ReplenishmentService();
    VehicleReportService vehicleReportService = new VehicleReportService();
    RoutingService routingService = new RoutingService();
    DispatchService dispatchService = new DispatchService(new DepotSelector(), new VehicleSelector(), routingService, eventBus);

    SimulationEngine engine = new SimulationEngine(
        graph,
        dispatchService,
        routingService,
        orderGenerator,
        movementService,
        trafficManager,
        metrics,
        eventBus,
        productCatalogService,
        inventoryService,
        replenishmentService,
        vehicleReportService,
        eventLogService,
        new InMemoryOrderRepository(),
        new com.tezprojesi.api.repository.InMemoryVehicleRepository(),
        depots,
        List.of(vehicle),
        new HashMap<>(Map.of("MAIN", 20.0, "M1", 0.0)),
        new HashMap<>(),
        1000,
        1.0,
        1.01,
        0,
        0.5,
        new HashMap<>(),
        750L,
        0.30,
        true);

    // Request is only 5% load (0.5 units out of 10.0 capacity)
    ReplenishmentRequest request = replenishmentService.create("MAIN", "M1", List.of(new OrderLine("P1", 1, 0.5)), 0L);
    engine.submitReplenishmentRequest(request);

    // At tick 300 (5 hours): Grace period just ended, aging is 0. Must NOT await approval.
    engine.stepTicks(300);
    assertTrue(engine.getDispatchReadiness().isEmpty() || !engine.getDispatchReadiness().get(0).awaitingApproval());

    // Advance to tick 530 (overdue = 230 ticks, aging reaches 1000 cap). Deadlock prevention triggers financial warning!
    engine.stepTicks(230);
    assertFalse(engine.getDispatchReadiness().isEmpty());
    assertTrue(engine.getDispatchReadiness().get(0).awaitingApproval(), "Critical low load must escalate at cap to prevent infinite deadlock");

    // Operator approves the force dispatch
    engine.approveReadyDispatch("V1");
    assertEquals(1, engine.getVehicleAssignments().size());
  }

  @Test
  void simulationEngine_capsQuadraticAgingScoreAtEightHundred() {
    CityGraph graph = new CityGraph();
    graph.addNode(new Node("MAIN", NodeType.MAIN_DEPOT));
    graph.addNode(new Node("M1", NodeType.MICRO_DEPOT));
    graph.addEdge(new Edge("MAIN", "M1", 5.0, 1.0));
    graph.addEdge(new Edge("M1", "MAIN", 5.0, 1.0));

    List<Depot> depots = List.of(
        new Depot("MAIN", DepotType.MAIN, "MAIN"),
        new Depot("M1", DepotType.MICRO, "M1"));

    Vehicle vehicle = new Vehicle(
        "V1",
        10.0,
        "MAIN",
        "DIESEL",
        1.0,
        Set.of(ProductCategory.FOOD),
        Set.of(com.tezprojesi.domain.VehicleCapability.STANDARD_BOX),
        "34CAP001",
        "Driver CAP",
        0.5,
        2,
        0,
        35.0,
        1000.0,
        1000.0,
        0,
        0);

    EventBus eventBus = new EventBus();
    ProductCatalogService productCatalogService = new ProductCatalogService();
    productCatalogService.save(new Product("P1", "Water", ProductCategory.FOOD, 1.0, 1.0, 100.0));
    InventoryService inventoryService = new InventoryService();
    inventoryService.setStock("MAIN", "P1", 20);
    inventoryService.setStock("M1", "P1", 0);

    OrderGenerator orderGenerator = new OrderGenerator(0.0, List.of("M1"), productCatalogService, new Random(0), eventBus);
    SimulationEventLogService eventLogService = new SimulationEventLogService();
    VehicleMovementService movementService = new VehicleMovementService(eventBus, eventLogService);
    TrafficManager trafficManager = new TrafficManager(new Random(0));
    MetricsEventHandler metrics = new MetricsEventHandler(eventBus);
    ReplenishmentService replenishmentService = new ReplenishmentService();
    VehicleReportService vehicleReportService = new VehicleReportService();
    RoutingService routingService = new RoutingService();
    DispatchService dispatchService = new DispatchService(new DepotSelector(), new VehicleSelector(), routingService, eventBus);

    SimulationEngine engine = new SimulationEngine(
        graph,
        dispatchService,
        routingService,
        orderGenerator,
        movementService,
        trafficManager,
        metrics,
        eventBus,
        productCatalogService,
        inventoryService,
        replenishmentService,
        vehicleReportService,
        eventLogService,
        new InMemoryOrderRepository(),
        new com.tezprojesi.api.repository.InMemoryVehicleRepository(),
        depots,
        List.of(vehicle),
        new HashMap<>(Map.of("MAIN", 20.0, "M1", 0.0)),
        new HashMap<>(),
        1000,
        1.0,
        1.01,
        0,
        0.5,
        new HashMap<>(),
        750L,
        0.30,
        true);

    // Request is 30% load (3 units / 10 capacity -> capacityScore = 300.0)
    ReplenishmentRequest request = replenishmentService.create("MAIN", "M1", List.of(new OrderLine("P1", 3, 1.0)), 0L);
    engine.submitReplenishmentRequest(request);

    // Advance 600 ticks.
    // Without cap: wait=600 -> 600^2 * 0.01 = 3600.0 -> planBenefit = 300 (cap) + 3600 (aging) + 300 (urgent) = 4200.0.
    // With cap (800.0): planBenefit = 300.0 (capacity) + 800.0 (capped aging) + 300.0 (urgent bonus) = 1400.0.
    engine.stepTicks(600);

    assertEquals(1400.0, request.getPlanBenefit(), 0.01,
        "Plan benefit should be exactly 300.0 (capacity) + 800.0 (capped aging) + 300.0 (urgent bonus), preventing unbounded quadratic inflation");
  }

  @Test
  void simulationEngine_bestFitSelection_prefersSmallerVehicleOverHeavyTruckForSmallCargo() {
    CityGraph graph = new CityGraph();
    graph.addNode(new Node("MAIN", NodeType.MAIN_DEPOT));
    graph.addNode(new Node("M1", NodeType.MICRO_DEPOT));
    graph.addNode(new Node("J1", NodeType.INTERSECTION));
    graph.addEdge(new Edge("MAIN", "J1", 1.0, 1.0));
    graph.addEdge(new Edge("J1", "MAIN", 1.0, 1.0));
    graph.addEdge(new Edge("J1", "M1", 1.0, 1.0));
    graph.addEdge(new Edge("M1", "J1", 1.0, 1.0));

    List<Depot> depots = List.of(
        new Depot("MAIN", DepotType.MAIN, "MAIN"),
        new Depot("M1", DepotType.MICRO, "M1"));

    // Fleet has HEAVY-TRUCK first in list, and ECO-VAN second.
    Vehicle heavyTruck = new Vehicle("HEAVY-TRUCK", 85.0, "MAIN");
    Vehicle ecoVan = new Vehicle("ECO-VAN", 10.0, "MAIN");

    EventBus eventBus = new EventBus();
    ProductCatalogService productCatalogService = new ProductCatalogService();
    productCatalogService.save(new Product("P1", "Water", ProductCategory.FOOD, 1.0, 1.0, 100.0));
    InventoryService inventoryService = new InventoryService();
    inventoryService.setStock("MAIN", "P1", 50);
    inventoryService.setStock("M1", "P1", 0);

    OrderGenerator orderGenerator = new OrderGenerator(0.0, List.of("M1"), productCatalogService, new Random(0), eventBus);
    SimulationEventLogService eventLogService = new SimulationEventLogService();
    VehicleMovementService movementService = new VehicleMovementService(eventBus, eventLogService);
    TrafficManager trafficManager = new TrafficManager(new Random(0));
    MetricsEventHandler metrics = new MetricsEventHandler(eventBus);
    ReplenishmentService replenishmentService = new ReplenishmentService();
    VehicleReportService vehicleReportService = new VehicleReportService();
    RoutingService routingService = new RoutingService();
    DispatchService dispatchService = new DispatchService(new DepotSelector(), new VehicleSelector(), routingService, eventBus);

    SimulationEngine engine = new SimulationEngine(
        graph,
        dispatchService,
        routingService,
        orderGenerator,
        movementService,
        trafficManager,
        metrics,
        eventBus,
        productCatalogService,
        inventoryService,
        replenishmentService,
        vehicleReportService,
        eventLogService,
        new InMemoryOrderRepository(),
        new com.tezprojesi.api.repository.InMemoryVehicleRepository(),
        depots,
        List.of(heavyTruck, ecoVan),
        new HashMap<>(Map.of("MAIN", 50.0, "M1", 0.0)),
        new HashMap<>(),
        10,
        1.0,
        1.01,
        0,
        0.5,
        new HashMap<>(),
        750L,
        0.30,
        true);

    // Small request: 8 m3.
    // Eco-Van achieves 80% load (>= 75% -> readyToDispatch, wastePenalty = 0.5)
    // Heavy-Truck achieves 9.4% load (< 50% -> grace period, wastePenalty = 19.25)
    ReplenishmentRequest request = replenishmentService.create("MAIN", "M1", List.of(new OrderLine("P1", 8, 1.0)), 0L);
    engine.submitReplenishmentRequest(request);

    engine.stepTicks(1);

    // Best-fit selection must pick ECO-VAN despite HEAVY-TRUCK being first in fleet list
    assertEquals(1, engine.getVehicleAssignments().size());
    assertEquals("ECO-VAN", request.getAssignedVehicleId(),
        "Best-fit matching must select ECO-VAN instead of locking HEAVY-TRUCK with small 8m3 cargo");
    assertEquals(com.tezprojesi.domain.ReplenishmentStatus.IN_TRANSIT, request.getStatus());
  }

  @Test
  void simulationEngine_agingDiscountIsCapped_longIdleTruckDoesNotBeatBetterFittingVan() {
    CityGraph graph = new CityGraph();
    graph.addNode(new Node("MAIN", NodeType.MAIN_DEPOT));
    graph.addNode(new Node("M1", NodeType.MICRO_DEPOT));
    graph.addNode(new Node("J1", NodeType.INTERSECTION));
    graph.addEdge(new Edge("MAIN", "J1", 1.0, 1.0));
    graph.addEdge(new Edge("J1", "MAIN", 1.0, 1.0));
    graph.addEdge(new Edge("J1", "M1", 1.0, 1.0));
    graph.addEdge(new Edge("M1", "J1", 1.0, 1.0));

    List<Depot> depots = List.of(
        new Depot("MAIN", DepotType.MAIN, "MAIN"),
        new Depot("M1", DepotType.MICRO, "M1"));

    // Truck (30 m3) has been idle since tick 0; van (20 m3) only since tick 240.
    // For an 18 m3 cargo both are dispatch-ready (van 90% load, truck 60% load + waited >= 180).
    // Uncapped aging (1.5/tick) would make the truck win; the cap must keep the van (better fit) first.
    Vehicle truck = new Vehicle("LONG-IDLE-TRUCK", 30.0, "MAIN");
    Vehicle van = new Vehicle("FRESH-VAN", 20.0, "MAIN");
    truck.setWaitingSinceTick(0L);
    van.setWaitingSinceTick(240L);

    EventBus eventBus = new EventBus();
    ProductCatalogService productCatalogService = new ProductCatalogService();
    productCatalogService.save(new Product("P1", "Water", ProductCategory.FOOD, 1.0, 1.0, 100.0));
    InventoryService inventoryService = new InventoryService();
    inventoryService.setStock("MAIN", "P1", 50);
    inventoryService.setStock("M1", "P1", 0);

    OrderGenerator orderGenerator = new OrderGenerator(0.0, List.of("M1"), productCatalogService, new Random(0), eventBus);
    SimulationEventLogService eventLogService = new SimulationEventLogService();
    VehicleMovementService movementService = new VehicleMovementService(eventBus, eventLogService);
    TrafficManager trafficManager = new TrafficManager(new Random(0));
    MetricsEventHandler metrics = new MetricsEventHandler(eventBus);
    ReplenishmentService replenishmentService = new ReplenishmentService();
    VehicleReportService vehicleReportService = new VehicleReportService();
    RoutingService routingService = new RoutingService();
    DispatchService dispatchService = new DispatchService(new DepotSelector(), new VehicleSelector(), routingService, eventBus);

    SimulationEngine engine = new SimulationEngine(
        graph,
        dispatchService,
        routingService,
        orderGenerator,
        movementService,
        trafficManager,
        metrics,
        eventBus,
        productCatalogService,
        inventoryService,
        replenishmentService,
        vehicleReportService,
        eventLogService,
        new InMemoryOrderRepository(),
        new com.tezprojesi.api.repository.InMemoryVehicleRepository(),
        depots,
        List.of(truck, van),
        new HashMap<>(Map.of("MAIN", 50.0, "M1", 0.0)),
        new HashMap<>(),
        1000,
        1.0,
        1.01,
        0,
        0.5,
        new HashMap<>(),
        750L,
        0.30,
        true);

    engine.stepTicks(250);

    ReplenishmentRequest request = replenishmentService.create("MAIN", "M1", List.of(new OrderLine("P1", 18, 1.0)), 250L);
    engine.submitReplenishmentRequest(request);
    engine.stepTicks(1);

    assertEquals("FRESH-VAN", request.getAssignedVehicleId(),
        "Capped aging discount must not let a long-idle truck override the better-fitting van");
  }

  @Test
  void simulationEngine_antiStarvation_parallelVehiclesClaimDifferentCorridors() {
    CityGraph graph = new CityGraph();
    graph.addNode(new Node("MAIN", NodeType.MAIN_DEPOT));
    graph.addNode(new Node("M1", NodeType.MICRO_DEPOT));
    graph.addNode(new Node("M2", NodeType.MICRO_DEPOT));
    graph.addNode(new Node("J1", NodeType.INTERSECTION));
    graph.addNode(new Node("J2", NodeType.INTERSECTION));

    // East Corridor: MAIN <-> J1 <-> M1 (15 km East)
    graph.addEdge(new Edge("MAIN", "J1", 10.0, 1.0));
    graph.addEdge(new Edge("J1", "MAIN", 10.0, 1.0));
    graph.addEdge(new Edge("J1", "M1", 5.0, 1.0));
    graph.addEdge(new Edge("M1", "J1", 5.0, 1.0));

    // West Corridor: MAIN <-> J2 <-> M2 (15 km West)
    graph.addEdge(new Edge("MAIN", "J2", 10.0, 1.0));
    graph.addEdge(new Edge("J2", "MAIN", 10.0, 1.0));
    graph.addEdge(new Edge("J2", "M2", 5.0, 1.0));
    graph.addEdge(new Edge("M2", "J2", 5.0, 1.0));

    List<Depot> depots = List.of(
        new Depot("MAIN", DepotType.MAIN, "MAIN"),
        new Depot("M1", DepotType.MICRO, "M1"),
        new Depot("M2", DepotType.MICRO, "M2"));

    Vehicle v1 = new Vehicle("V1", 20.0, "MAIN");
    Vehicle v2 = new Vehicle("V2", 20.0, "MAIN");

    EventBus eventBus = new EventBus();
    ProductCatalogService productCatalogService = new ProductCatalogService();
    productCatalogService.save(new Product("P1", "Water", ProductCategory.FOOD, 1.0, 1.0, 100.0));
    InventoryService inventoryService = new InventoryService();
    inventoryService.setStock("MAIN", "P1", 50);
    inventoryService.setStock("M1", "P1", 0);
    inventoryService.setStock("M2", "P1", 0);

    OrderGenerator orderGenerator = new OrderGenerator(0.0, List.of("M1", "M2"), productCatalogService, new Random(0), eventBus);
    SimulationEventLogService eventLogService = new SimulationEventLogService();
    VehicleMovementService movementService = new VehicleMovementService(eventBus, eventLogService);
    TrafficManager trafficManager = new TrafficManager(new Random(0));
    MetricsEventHandler metrics = new MetricsEventHandler(eventBus);
    ReplenishmentService replenishmentService = new ReplenishmentService();
    VehicleReportService vehicleReportService = new VehicleReportService();
    RoutingService routingService = new RoutingService();
    DispatchService dispatchService = new DispatchService(new DepotSelector(), new VehicleSelector(), routingService, eventBus);

    SimulationEngine engine = new SimulationEngine(
        graph,
        dispatchService,
        routingService,
        orderGenerator,
        movementService,
        trafficManager,
        metrics,
        eventBus,
        productCatalogService,
        inventoryService,
        replenishmentService,
        vehicleReportService,
        eventLogService,
        new InMemoryOrderRepository(),
        new com.tezprojesi.api.repository.InMemoryVehicleRepository(),
        depots,
        List.of(v1, v2),
        new HashMap<>(Map.of("MAIN", 50.0, "M1", 0.0, "M2", 0.0)),
        new HashMap<>(),
        10,
        1.0,
        1.01,
        0,
        0.5,
        new HashMap<>(),
        750L,
        0.30,
        true);

    // Two small requests: one for East (M1) and one for West (M2)
    ReplenishmentRequest reqEast = replenishmentService.create("MAIN", "M1", List.of(new OrderLine("P1", 4, 1.0)), 0L);
    ReplenishmentRequest reqWest = replenishmentService.create("MAIN", "M2", List.of(new OrderLine("P1", 4, 1.0)), 0L);
    engine.submitReplenishmentRequest(reqEast);
    engine.submitReplenishmentRequest(reqWest);

    engine.stepTicks(1);

    List<SimulationEngine.DispatchReadinessSnapshot> readinessList = engine.getDispatchReadiness();
    SimulationEngine.DispatchReadinessSnapshot v1Snapshot = readinessList.stream()
        .filter(s -> s.vehicleId().equals("V1"))
        .findFirst()
        .orElse(null);
    SimulationEngine.DispatchReadinessSnapshot v2Snapshot = readinessList.stream()
        .filter(s -> s.vehicleId().equals("V2"))
        .findFirst()
        .orElse(null);

    assertNotNull(v1Snapshot, "V1 should have a readiness plan");
    assertNotNull(v2Snapshot, "V2 should have a readiness plan");

    assertTrue(v1Snapshot.targetDepotIds().contains("M1") || v1Snapshot.targetDepotIds().contains("M2"));
    assertTrue(v2Snapshot.targetDepotIds().contains("M1") || v2Snapshot.targetDepotIds().contains("M2"));
    assertFalse(v1Snapshot.targetDepotIds().equals(v2Snapshot.targetDepotIds()),
        "Anti-starvation partitioning must assign different corridors to V1 and V2: v1="
            + v1Snapshot.targetDepotIds() + ", v2=" + v2Snapshot.targetDepotIds());
  }

  @Test
  void simulationEngine_capabilityProtectedAgingDiscount_prioritizesWaitingVehicleAndProtectsCapabilities() {
    CityGraph graph = new CityGraph();
    graph.addNode(new Node("MAIN", NodeType.MAIN_DEPOT));
    graph.addNode(new Node("M1", NodeType.MICRO_DEPOT));
    graph.addNode(new Node("J1", NodeType.INTERSECTION));
    graph.addEdge(new Edge("MAIN", "J1", 1.0, 1.0));
    graph.addEdge(new Edge("J1", "MAIN", 1.0, 1.0));
    graph.addEdge(new Edge("J1", "M1", 1.0, 1.0));
    graph.addEdge(new Edge("M1", "J1", 1.0, 1.0));

    List<Depot> depots = List.of(
        new Depot("MAIN", DepotType.MAIN, "MAIN"),
        new Depot("M1", DepotType.MICRO, "M1"));

    // Vehicle 1: V_INCOMPATIBLE_OLD: has waited 200 ticks, but lacks COOLED_BOX
    Vehicle vIncompatibleOld = new Vehicle(
        "V_INCOMPATIBLE_OLD",
        10.0,
        "MAIN",
        "DIESEL",
        1.0,
        Set.of(ProductCategory.FOOD),
        Set.of(com.tezprojesi.domain.VehicleCapability.STANDARD_BOX),
        "34V9001",
        "Driver 1",
        0.5,
        2,
        0,
        10.0,
        8.0,
        1,
        1);
    vIncompatibleOld.setWaitingSinceTick(0L);

    // Vehicle 2: V_COMPATIBLE_NEW: newly idle (waited 0 ticks), has COOLED_BOX
    Vehicle vCompatibleNew = new Vehicle(
        "V_COMPATIBLE_NEW",
        10.0,
        "MAIN",
        "DIESEL",
        1.0,
        Set.of(ProductCategory.FOOD),
        Set.of(com.tezprojesi.domain.VehicleCapability.COOLED_BOX, com.tezprojesi.domain.VehicleCapability.STANDARD_BOX),
        "34V9002",
        "Driver 2",
        0.5,
        2,
        0,
        10.0,
        8.0,
        1,
        1);
    vCompatibleNew.setWaitingSinceTick(100L);

    // Vehicle 3: V_COMPATIBLE_OLD: waited 80 ticks at MAIN, has COOLED_BOX
    Vehicle vCompatibleOld = new Vehicle(
        "V_COMPATIBLE_OLD",
        10.0,
        "MAIN",
        "DIESEL",
        1.0,
        Set.of(ProductCategory.FOOD),
        Set.of(com.tezprojesi.domain.VehicleCapability.COOLED_BOX, com.tezprojesi.domain.VehicleCapability.STANDARD_BOX),
        "34V9003",
        "Driver 3",
        0.5,
        2,
        0,
        10.0,
        8.0,
        1,
        1);
    vCompatibleOld.setWaitingSinceTick(20L); // Waited 80 ticks by tick 100

    EventBus eventBus = new EventBus();
    ProductCatalogService productCatalogService = new ProductCatalogService();
    // Cooled box product!
    productCatalogService.save(new Product(
        "P_COLD",
        "Vaccine",
        ProductCategory.FOOD,
        1.0,
        1.0,
        500.0,
        Set.of(com.tezprojesi.domain.ProductCapability.COOLED_BOX)));
    InventoryService inventoryService = new InventoryService();
    inventoryService.setStock("MAIN", "P_COLD", 50);
    inventoryService.setStock("M1", "P_COLD", 0);

    OrderGenerator orderGenerator = new OrderGenerator(0.0, List.of("M1"), productCatalogService, new Random(0), eventBus);
    SimulationEventLogService eventLogService = new SimulationEventLogService();
    VehicleMovementService movementService = new VehicleMovementService(eventBus, eventLogService);
    TrafficManager trafficManager = new TrafficManager(new Random(0));
    MetricsEventHandler metrics = new MetricsEventHandler(eventBus);
    ReplenishmentService replenishmentService = new ReplenishmentService();
    VehicleReportService vehicleReportService = new VehicleReportService();
    RoutingService routingService = new RoutingService();
    DispatchService dispatchService = new DispatchService(new DepotSelector(), new VehicleSelector(), routingService, eventBus);

    SimulationEngine engine = new SimulationEngine(
        graph,
        dispatchService,
        routingService,
        orderGenerator,
        movementService,
        trafficManager,
        metrics,
        eventBus,
        productCatalogService,
        inventoryService,
        replenishmentService,
        vehicleReportService,
        eventLogService,
        new InMemoryOrderRepository(),
        new com.tezprojesi.api.repository.InMemoryVehicleRepository(),
        depots,
        // List order has V_INCOMPATIBLE_OLD first, then V_COMPATIBLE_NEW, then V_COMPATIBLE_OLD
        List.of(vIncompatibleOld, vCompatibleNew, vCompatibleOld),
        new HashMap<>(Map.of("MAIN", 50.0, "M1", 0.0)),
        new HashMap<>(),
        1000,
        1.0,
        1.01,
        0,
        0.5,
        new HashMap<>(),
        750L,
        0.30,
        true);

    // Fast-forward tick to 100 so waiting calculations are accurate:
    // vIncompatibleOld wait: 100 - 0 = 100 ticks (but incompatible)
    // vCompatibleNew wait: 100 - 100 = 0 ticks
    // vCompatibleOld wait: 100 - 20 = 80 ticks
    for (int t = 0; t < 99; t++) {
      engine.stepTicks(1);
    }

    // Request requires COOLED_BOX and is 8 m3 (80% load -> readyToDispatch)
    ReplenishmentRequest request = replenishmentService.create("MAIN", "M1", List.of(new OrderLine("P_COLD", 8, 1.0)), 100L);
    engine.submitReplenishmentRequest(request);

    // Step at tick 100:
    engine.stepTicks(1);

    assertEquals("V_COMPATIBLE_OLD", request.getAssignedVehicleId(),
        "Stage 1 must eliminate incompatible vehicle despite waiting, and Stage 3 must prioritize V_COMPATIBLE_OLD over V_COMPATIBLE_NEW due to aging discount!");
    assertEquals(com.tezprojesi.domain.ReplenishmentStatus.IN_TRANSIT, request.getStatus());
    assertEquals(1, engine.getVehicleAssignments().size());
  }
}



