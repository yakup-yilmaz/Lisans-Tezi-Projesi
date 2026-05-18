package com.tezprojesi.simulation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
}



