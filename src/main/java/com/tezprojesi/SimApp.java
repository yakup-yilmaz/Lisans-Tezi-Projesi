package com.tezprojesi;

import com.tezprojesi.api.config.SimulationProperties;
import com.tezprojesi.api.repository.InMemoryOrderRepository;
import com.tezprojesi.api.repository.InMemoryVehicleRepository;
import com.tezprojesi.api.repository.OrderRepository;
import com.tezprojesi.api.repository.VehicleRepository;
import com.tezprojesi.dispatch.DepotSelector;
import com.tezprojesi.dispatch.DispatchService;
import com.tezprojesi.dispatch.VehicleSelector;
import com.tezprojesi.events.EventBus;
import com.tezprojesi.routing.CityGraph;
import com.tezprojesi.routing.RoutingService;
import com.tezprojesi.domain.Depot;
import com.tezprojesi.domain.Vehicle;
import com.tezprojesi.simulation.MetricsEventHandler;
import com.tezprojesi.simulation.OrderGenerator;
import com.tezprojesi.simulation.ProductCatalogService;
import com.tezprojesi.simulation.ReplenishmentService;
import com.tezprojesi.simulation.SimulationEngine;
import com.tezprojesi.simulation.TrafficManager;
import com.tezprojesi.simulation.SimulationEventLogService;
import com.tezprojesi.simulation.VehicleMovementService;
import com.tezprojesi.simulation.InventoryService;
import com.tezprojesi.simulation.VehicleReportService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.ArrayList;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(SimulationProperties.class)
public class SimApp {

  public static void main(String[] args) {
    SpringApplication.run(SimApp.class, args);
  }

  @Bean public EventBus eventBus() { return new EventBus(); }

  @Bean public CityGraph cityGraph() { return new CityGraph(); }

  @Bean public DepotSelector depotSelector() { return new DepotSelector(); }

  @Bean public VehicleSelector vehicleSelector(ProductCatalogService productCatalogService) {
    return new VehicleSelector(productCatalogService);
  }

  @Bean public RoutingService routingService() {
    return new RoutingService();
  }

  @Bean public DispatchService dispatchService(DepotSelector depotSelector, VehicleSelector vehicleSelector, RoutingService routingService, EventBus eventBus) {
    return new DispatchService(depotSelector, vehicleSelector, routingService, eventBus);
  }

  @Bean public MetricsEventHandler metricsEventHandler(EventBus eventBus) { return new MetricsEventHandler(eventBus); }

  @Bean public OrderRepository orderRepository() { return new InMemoryOrderRepository(); }

  @Bean public VehicleRepository vehicleRepository() { return new InMemoryVehicleRepository(); }

  @Bean public TrafficManager trafficManager() { return new TrafficManager(new Random()); }

  @Bean public ProductCatalogService productCatalogService() { return new ProductCatalogService(); }

  @Bean public InventoryService inventoryService() { return new InventoryService(); }

  @Bean public ReplenishmentService replenishmentService() { return new ReplenishmentService(); }

  @Bean public VehicleReportService vehicleReportService() { return new VehicleReportService(); }

  @Bean public SimulationEventLogService simulationEventLogService() { return new SimulationEventLogService(); }

  @Bean public VehicleMovementService vehicleMovementService(EventBus eventBus, SimulationEventLogService simulationEventLogService) {
    return new VehicleMovementService(eventBus, simulationEventLogService);
  }

  @Bean public Map<String, Long> orderCreatedTick() { return new HashMap<>(); }

  @Bean public Map<String, Double> depotStock() { return new HashMap<>(); }

  @Bean public Map<String, Long> vehicleWaitingSinceAtMain() { return new HashMap<>(); }

  @Bean public List<Depot> depots() {
    return new ArrayList<>();
  }

  @Bean public List<Vehicle> vehicles() {
    return new ArrayList<>();
  }

  @Bean public OrderGenerator orderGenerator(
      EventBus eventBus, SimulationProperties props, ProductCatalogService productCatalogService) {
    return new OrderGenerator(
        props.getOrderRate(),
        List.of("M1", "M2", "M3", "M4", "M5"),
        productCatalogService,
        new Random(),
        eventBus);
  }

  @Bean public SimulationEngine simulationEngine(
      CityGraph graph,
      DispatchService dispatchService,
      RoutingService routingService,
      OrderGenerator orderGenerator,
      VehicleMovementService vehicleMovementService,
      TrafficManager trafficManager,
      MetricsEventHandler metricsEventHandler,
      EventBus eventBus,
      ProductCatalogService productCatalogService,
      InventoryService inventoryService,
      ReplenishmentService replenishmentService,
      VehicleReportService vehicleReportService,
      SimulationEventLogService simulationEventLogService,
      OrderRepository orderRepository,
      VehicleRepository vehicleRepository,
      List<Depot> depots,
      List<Vehicle> vehicles,
      Map<String, Double> depotStock,
      Map<String, Long> orderCreatedTick,
      SimulationProperties props,
      Map<String, Long> vehicleWaitingSinceAtMain) {
    long ticksPerHour = Math.max(1, 60L / Math.max(1, props.getTickDurationMinutes()));
    long maxMainWaitTicks = (long) Math.round(props.getMaxMainWaitHours() * ticksPerHour);
    return new SimulationEngine(
        graph,
        dispatchService,
        routingService,
        orderGenerator,
        vehicleMovementService,
        trafficManager,
        metricsEventHandler,
        eventBus,
        productCatalogService,
        inventoryService,
        replenishmentService,
        vehicleReportService,
        simulationEventLogService,
        orderRepository,
        vehicleRepository,
        depots,
        vehicles,
        depotStock,
        orderCreatedTick,
        props.getMaxTicks(),
        props.getMinTrafficFactor(),
        props.getMaxTrafficFactor(),
        maxMainWaitTicks,
        props.getMinMainDepartureLoadFraction(),
        vehicleWaitingSinceAtMain,
        props.getLiveTickDelayMs(),
        props.getReorderPointFraction(),
        props.isDispatchApprovalRequired());
  }
}
