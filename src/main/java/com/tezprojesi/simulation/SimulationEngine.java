package com.tezprojesi.simulation;

import com.tezprojesi.api.exception.BadRequestException;

import com.tezprojesi.api.repository.OrderRepository;
import com.tezprojesi.api.repository.VehicleRepository;
import com.tezprojesi.domain.CapabilityMasks;
import com.tezprojesi.domain.Depot;
import com.tezprojesi.domain.DepotType;
import com.tezprojesi.domain.Edge;
import com.tezprojesi.domain.Node;
import com.tezprojesi.domain.Order;
import com.tezprojesi.domain.OrderLine;
import com.tezprojesi.domain.OrderStatus;
import com.tezprojesi.domain.Product;
import com.tezprojesi.domain.ProductCapability;
import com.tezprojesi.domain.ProductCategory;
import com.tezprojesi.domain.ReplenishmentRequest;
import com.tezprojesi.domain.ReplenishmentStatus;
import com.tezprojesi.domain.Vehicle;
import com.tezprojesi.domain.VehicleStatus;
import com.tezprojesi.events.EventBus;
import com.tezprojesi.events.OrderCreatedEvent;
import com.tezprojesi.routing.CityGraph;
import com.tezprojesi.routing.RoutingService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.concurrent.ThreadLocalRandom;

public final class SimulationEngine {

  public static final long TICK_MINUTES = 1L;
  private static final double TARGET_STOCK_FRACTION = 1.0;
  private static final long REPLENISHMENT_COOLDOWN_TICKS = 10L; // RULE: 10-tick cooldown per depot-product pair
  private static final int MAX_DEMAND_DRIVEN_SHIPMENT_UNITS = 50;
  private static final long URGENT_ORDER_DELAY_TICKS = 30L;
  private static final double ROUTE_COST_WEIGHT = 1.0;
  /** Per master spec: labor cost = handlingTicks × rate. */
  private static final double LABOR_RATE_PER_TICK = 0.55;
  private static final double DELAY_PENALTY_WEIGHT = 0.08;
  private static final double STOCKOUT_PENALTY_WEIGHT = 2.5;
  private static final double OPPORTUNITY_COST_WEIGHT = 0.0;
  private static final double TIME_COST_WEIGHT = 0.35;
  /** Wait-time inflation: effective unit value = base + depotWaitTicks × 1.5 */
  private static final double WAIT_VALUE_INFLATOR_PER_TICK = 1.5;
  /**
   * RULE: Max 3 micro-depot deliveries (main depot + up to 3 micro-depot stops =
   * 4 total legs).
   */
  private static final int MAX_STOPS_PER_TRIP = 3;
  /**
   * Dynamic bypass: max extra delivery km = 3 + (total cargo financial value /
   * 100).
   */
  private static final double DYNAMIC_BYPASS_BASE_KM = 3.0;
  // Logistics rule: each extra stop adds a fixed operational handling penalty.
  private static final double EXTRA_STOP_PENALTY = 3.5;
  private static final double ELECTRIC_FUEL_COST_MULTIPLIER = 0.30;
  private static final double DIESEL_FUEL_COST_MULTIPLIER = 0.55;
  private static final double GASOLINE_FUEL_COST_MULTIPLIER = 0.75;
  private static final double DEFAULT_FUEL_COST_MULTIPLIER = DIESEL_FUEL_COST_MULTIPLIER;
  // RULE: Plans whose delivery route exceeds baseline distance by more than 50%
  // are escalated for human approval.
  private static final double CRITICAL_ROUTE_DEVIATION_RATIO = 0.50;
  private static final double HEAVY_VEHICLE_CAPACITY_THRESHOLD = 46.0;
  private static final double HEAVY_VEHICLE_TOLL_MULTIPLIER = 1.35;
  /**
   * Bonus when route stays on low-traffic edges (encourages “clean”
   * alternatives).
   */
  private static final double TRAFFIC_ESCAPE_BONUS = 28.0;

  private final CityGraph graph;
  private final RoutingService routingService;
  private final OrderGenerator orderGenerator;
  private final VehicleMovementService vehicleMovementService;
  private final TrafficManager trafficManager;
  private final MetricsEventHandler metricsEventHandler;
  private final ProductCatalogService productCatalogService;
  private final InventoryService inventoryService;
  private final ReplenishmentService replenishmentService;
  private final VehicleReportService vehicleReportService;
  private final SimulationEventLogService eventLogService;
  private final OrderRepository orderRepository;
  private final VehicleRepository vehicleRepository;
  private final List<Depot> depots;
  private final List<Vehicle> vehicles;
  private final Map<String, Order> activeOrders = new HashMap<>();
  private final List<Order> pendingOrders = new ArrayList<>();
  private final Map<String, Long> orderCreatedTick;
  private final Map<String, ReplenishmentRequest> activeReplenishments = new HashMap<>();
  private final Map<String, DeliveryPlan> stagedPlansByVehicle = new HashMap<>();
  /**
   * Guarded by approvalLock — accessed from both simulation-engine thread and
   * REST threads.
   */
  private final Map<String, DeliveryPlan> approvalPendingPlansByVehicle = new HashMap<>();
  /**
   * Tracks the tick at which each vehicle entered WAITING_FOR_APPROVAL, for
   * timeout enforcement.
   */
  private final Map<String, Long> approvalWaitingSinceTick = new HashMap<>();
  /**
   * Per-vehicle random approval window (45–60 ticks) once WAITING_FOR_APPROVAL
   * starts.
   */
  private final Map<String, Long> approvalTimeoutLimitTicks = new HashMap<>();
  private final Map<String, DispatchReadinessSnapshot> dispatchReadinessByVehicle = new HashMap<>();
  private final Set<String> vehiclesCompletedThisTick = new HashSet<>();
  private final Set<String> plannedRequestIds = new HashSet<>();
  private final Map<String, Long> lastDemandDrivenReplenishmentTickByProduct = new HashMap<>();
  private final Map<String, Long> vehicleIdleTicks = new HashMap<>();
  private final Map<String, Long> vehicleWorkingTicks = new HashMap<>();
  /**
   * Per-tick deduplication guard so recordVehicleUtilizationTick is called at
   * most once per vehicle per tick.
   */
  private final Set<String> utilityRecordedThisTick = new HashSet<>();
  /**
   * Dedup for one-shot %50+ capacity UI / event notification per vehicle per
   * waiting episode.
   */
  private final Set<String> capacityBannerEmittedForVehicle = new HashSet<>();
  /**
   * Protects approvalPendingPlansByVehicle from concurrent REST-thread +
   * simulation-thread access.
   */
  private final ReentrantLock approvalLock = new ReentrantLock();
  private final int maxTicks;
  private final double minTrafficFactor;
  private final double maxTrafficFactor;
  private final boolean requireManualDispatchApproval;
  /** Mikro depo otomatik talep eşiği: stok baseline × bu oran. */
  private final double reorderPointFraction;
  private final EventBus eventBus;
  private final Object lifecycleMonitor = new Object();
  private volatile boolean running = false;
  private volatile boolean stopRequested = false;
  private volatile String lastFailureMessage = null;
  private volatile long lastFailureTick = -1L;
  private volatile long currentTick = 0;
  private final long liveTickDelayMs;
  private boolean initialStateCaptured = false;
  private boolean replenishmentPlanDirty = true;
  private List<Node> initialNodes = List.of();
  private List<Edge> initialEdges = List.of();
  private List<Depot> initialDepots = List.of();
  private List<Product> initialProducts = List.of();
  private List<Vehicle> initialVehicles = List.of();
  private Map<String, Map<String, Integer>> initialInventorySnapshot = Map.of();

  public SimulationEngine(
      CityGraph graph,
      com.tezprojesi.dispatch.DispatchService dispatchService,
      OrderGenerator orderGenerator,
      VehicleMovementService vehicleMovementService,
      TrafficManager trafficManager,
      MetricsEventHandler metricsEventHandler,
      EventBus eventBus,
      ProductCatalogService productCatalogService,
      InventoryService inventoryService,
      ReplenishmentService replenishmentService,
      VehicleReportService vehicleReportService,
      SimulationEventLogService eventLogService,
      OrderRepository orderRepository,
      VehicleRepository vehicleRepository,
      List<Depot> depots,
      List<Vehicle> vehicles,
      Map<String, Double> depotStock,
      Map<String, Long> orderCreatedTick,
      int maxTicks,
      double minTrafficFactor,
      double maxTrafficFactor,
      long maxMainWaitTicks,
      double minMainDepartureLoadFraction,
      Map<String, Long> vehicleWaitingSinceAtMain) {
    this(
        graph,
        dispatchService,
        new RoutingService(),
        orderGenerator,
        vehicleMovementService,
        trafficManager,
        metricsEventHandler,
        eventBus,
        productCatalogService,
        inventoryService,
        replenishmentService,
        vehicleReportService,
        eventLogService,
        orderRepository,
        vehicleRepository,
        depots,
        vehicles,
        depotStock,
        orderCreatedTick,
        maxTicks,
        minTrafficFactor,
        maxTrafficFactor,
        maxMainWaitTicks,
        minMainDepartureLoadFraction,
        vehicleWaitingSinceAtMain,
        750L,
        0.30,
        false);
  }

  public SimulationEngine(
      CityGraph graph,
      com.tezprojesi.dispatch.DispatchService dispatchService,
      OrderGenerator orderGenerator,
      VehicleMovementService vehicleMovementService,
      TrafficManager trafficManager,
      MetricsEventHandler metricsEventHandler,
      EventBus eventBus,
      ProductCatalogService productCatalogService,
      InventoryService inventoryService,
      ReplenishmentService replenishmentService,
      VehicleReportService vehicleReportService,
      SimulationEventLogService eventLogService,
      OrderRepository orderRepository,
      VehicleRepository vehicleRepository,
      List<Depot> depots,
      List<Vehicle> vehicles,
      Map<String, Double> depotStock,
      Map<String, Long> orderCreatedTick,
      int maxTicks,
      double minTrafficFactor,
      double maxTrafficFactor,
      long maxMainWaitTicks,
      double minMainDepartureLoadFraction,
      Map<String, Long> vehicleWaitingSinceAtMain,
      long liveTickDelayMs) {
    this(
        graph,
        dispatchService,
        new RoutingService(),
        orderGenerator,
        vehicleMovementService,
        trafficManager,
        metricsEventHandler,
        eventBus,
        productCatalogService,
        inventoryService,
        replenishmentService,
        vehicleReportService,
        eventLogService,
        orderRepository,
        vehicleRepository,
        depots,
        vehicles,
        depotStock,
        orderCreatedTick,
        maxTicks,
        minTrafficFactor,
        maxTrafficFactor,
        maxMainWaitTicks,
        minMainDepartureLoadFraction,
        vehicleWaitingSinceAtMain,
        liveTickDelayMs,
        0.30,
        false);
  }

  public SimulationEngine(
      CityGraph graph,
      com.tezprojesi.dispatch.DispatchService dispatchService,
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
      SimulationEventLogService eventLogService,
      OrderRepository orderRepository,
      VehicleRepository vehicleRepository,
      List<Depot> depots,
      List<Vehicle> vehicles,
      Map<String, Double> depotStock,
      Map<String, Long> orderCreatedTick,
      int maxTicks,
      double minTrafficFactor,
      double maxTrafficFactor,
      long maxMainWaitTicks,
      double minMainDepartureLoadFraction,
      Map<String, Long> vehicleWaitingSinceAtMain,
      long liveTickDelayMs,
      double reorderPointFraction,
      boolean requireManualDispatchApproval) {
    this.graph = Objects.requireNonNull(graph, "graph");
    this.routingService = Objects.requireNonNull(routingService, "routingService");
    this.orderGenerator = Objects.requireNonNull(orderGenerator, "orderGenerator");
    this.vehicleMovementService = Objects.requireNonNull(vehicleMovementService, "vehicleMovementService");
    this.trafficManager = Objects.requireNonNull(trafficManager, "trafficManager");

    // OPTIMIZATION: Connect TrafficManager to RoutingService for cache invalidation
    this.trafficManager.setRoutingService(this.routingService);
    this.metricsEventHandler = Objects.requireNonNull(metricsEventHandler, "metricsEventHandler");
    this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
    this.productCatalogService = Objects.requireNonNull(productCatalogService, "productCatalogService");
    this.inventoryService = Objects.requireNonNull(inventoryService, "inventoryService");
    this.replenishmentService = Objects.requireNonNull(replenishmentService, "replenishmentService");
    this.vehicleReportService = Objects.requireNonNull(vehicleReportService, "vehicleReportService");
    this.eventLogService = Objects.requireNonNull(eventLogService, "eventLogService");
    this.orderRepository = Objects.requireNonNull(orderRepository, "orderRepository");
    this.vehicleRepository = Objects.requireNonNull(vehicleRepository, "vehicleRepository");
    this.depots = Objects.requireNonNull(depots, "depots");
    this.vehicles = Objects.requireNonNull(vehicles, "vehicles");
    this.orderCreatedTick = Objects.requireNonNull(orderCreatedTick, "orderCreatedTick");
    this.maxTicks = maxTicks;
    this.minTrafficFactor = minTrafficFactor;
    this.maxTrafficFactor = maxTrafficFactor;
    this.liveTickDelayMs = Math.max(100L, liveTickDelayMs);
    if (reorderPointFraction <= 0 || reorderPointFraction > 1) {
      throw new IllegalArgumentException("reorderPointFraction must be in (0,1]");
    }
    this.reorderPointFraction = reorderPointFraction;
    this.requireManualDispatchApproval = requireManualDispatchApproval;
  }

  public double getReorderPointFraction() {
    return reorderPointFraction;
  }

  public void run() {
    synchronized (lifecycleMonitor) {
      if (running) {
        throw new IllegalStateException("Simulation already running");
      }
      running = true;
      stopRequested = false;
      lastFailureMessage = null;
      lastFailureTick = -1L;
    }
    executeRunLoop();
  }

  private void executeRunLoop() {
    try {
      boolean initialLogRecorded = false;
      while (!stopRequested && currentTick < maxTicks) {
        // İlk tick'i log et
        if (!initialLogRecorded && currentTick == 0) {
          eventLogService.record(currentTick, "SIMULATION_STARTED",
              "Simülasyon başladı",
              "Maksimum " + maxTicks + " dakika çalışacak. Hız: 1 dakika = " + liveTickDelayMs + "ms",
              "SIMULATION", "engine");
          initialLogRecorded = true;
        }

        // Tick başlangıcı zamanını kaydet (Adaptive Timing)
        long tickStartTime = System.currentTimeMillis();

        synchronized (lifecycleMonitor) {
          stepTicks(5);
        }

        // Adaptive timing: işlem süresi kadarını skip et
        long elapsedMs = System.currentTimeMillis() - tickStartTime;
        long adjustedSleepMs = Math.max(10L, liveTickDelayMs - elapsedMs);

        try {
          Thread.sleep(adjustedSleepMs);
        } catch (InterruptedException exception) {
          Thread.currentThread().interrupt();
          synchronized (lifecycleMonitor) {
            stopRequested = true;
          }
          break;
        }
      }
    } catch (RuntimeException exception) {
      recordFailure(exception);
      exception.printStackTrace();
    } finally {
      synchronized (lifecycleMonitor) {
        running = false;
        String reason = stopRequested ? "Kullanici tarafindan durduruldu"
            : (currentTick >= maxTicks ? "Simulasyon suresi doldu (Tamamlandi)"
                : "Beklenmeyen bir hata olustu");
        eventLogService.record(currentTick, "SIMULATION_STOPPED", "Simulasyon durdu", reason, "SIMULATION", "engine");
      }
    }
  }

  public void stepTicks(long tickCount) {
    if (!running) {
      stopRequested = false;
    }
    long targetTick = Math.min(maxTicks, currentTick + Math.max(0, tickCount));
    for (long tick = currentTick + 1; tick <= targetTick; tick++) {
      if (stopRequested) {
        break;
      }
      vehiclesCompletedThisTick.clear();
      utilityRecordedThisTick.clear();
      currentTick = tick;
      inventoryService.setSimulationClock(currentTick);
      vehicleMovementService.setCurrentTick(currentTick);

      // OPTIMIZATION: Use Rush-Hour traffic model instead of uniform random
      trafficManager.updateTrafficWithRushHourModel(graph, currentTick);

      replenishmentPlanDirty = true;
      vehicleMovementService.refreshTravelDurations(graph, vehicles);
      updateVehicleUtilization();

      List<Order> newOrders = orderGenerator.generateOrders(tick, depots, graph);
      for (Order order : newOrders) {
        submitOrder(order);
      }

      reevaluateReplenishmentQueue();
      handlePendingOrders();
      autoRefillMainDepot();
      handleVehicleCompletions();
      // RULE: Check approval timeouts before planning so expired approvals are
      // cleared first.
      checkApprovalTimeouts();
      handleActiveReplenishments();
      vehiclesCompletedThisTick.clear();
    }
  }

  private void updateVehicleUtilization() {
    for (Vehicle vehicle : vehicles) {
      // RULE: Deduplication guard — recordVehicleUtilizationTick must fire at most
      // once per vehicle per tick.
      // Without this, DeliveryCompletedEvent handler in MetricsEventHandler would
      // cause double-counting.
      boolean activeRoute = vehicleMovementService.hasActiveRoute(vehicle.getId());
      boolean alsoWorking = vehicle.getStatus() == VehicleStatus.LOADING
          || vehicle.getStatus() == VehicleStatus.DELIVERING
          || vehicle.getStatus() == VehicleStatus.EN_ROUTE;
      if (activeRoute || alsoWorking) {
        vehicleWorkingTicks.merge(vehicle.getId(), TICK_MINUTES, Long::sum);
        // Guard: record utilization only once per vehicle per tick.
        if (utilityRecordedThisTick.add(vehicle.getId())) {
          metricsEventHandler.recordVehicleUtilizationTick(vehicle.getId(), TICK_MINUTES);
        }
      } else {
        vehicleIdleTicks.merge(vehicle.getId(), TICK_MINUTES, Long::sum);
      }
    }
  }

  private void handlePendingOrders() {
    Iterator<Order> iterator = pendingOrders.iterator();
    while (iterator.hasNext()) {
      Order order = iterator.next();
      String depotId = order.getMicroDepotNodeId();

      List<OrderLine> newlyReserved = inventoryService.reserveAvailable(depotId, order.getUnreservedItems());
      order.reserveItems(newlyReserved);

      if (order.isFullyReserved()) {
        List<OrderLine> reservedItems = order.getReservedItems();
        inventoryService.consumeReserved(depotId, reservedItems);
        recordDepotStockChange(depotId, reservedItems, -1);
        order.clearReservedItems();
        order.fulfillFromMicroStock();
        order.complete();
        metricsEventHandler.recordFulfilledFromMicro(order.getId(), deliveryTicks(order.getId()));
        eventLogService.record(currentTick, "ORDER_FULFILLED", "Siparis mikro depodan karsilandi",
            order.getId() + " siparisi " + depotId + " mikro deposundan karsilandi.", "ORDER", order.getId());
        iterator.remove();
        triggerDemandDrivenReplenishment(depotId);
        continue;
      }

      order.waitForReplenishment();
      triggerDemandDrivenReplenishment(depotId);
    }
  }

  private void reevaluateReplenishmentQueue() {
    for (Depot depot : depots) {
      if (depot.isMicro()) {
        triggerDemandDrivenReplenishment(depot.getId());
      }
    }
  }

  private void triggerDemandDrivenReplenishment(String depotId) {
    Depot mainDepot = mainDepot();
    if (mainDepot == null || mainDepot.getId().equals(depotId)) {
      return;
    }

    Set<String> relevantProducts = new HashSet<>(inventoryService.getDepotStock(depotId).keySet());
    pendingOrders.stream()
        .filter(order -> depotId.equals(order.getMicroDepotNodeId()))
        .flatMap(order -> order.getItems().stream())
        .map(OrderLine::getProductId)
        .forEach(relevantProducts::add);

    for (String productId : relevantProducts) {
      Product product = productCatalogService.findById(productId).orElse(null);
      if (product == null) {
        continue;
      }
      // RULE: 10-tick cooldown — same depot-product pair cannot trigger a new request
      // within 10 ticks.
      String cooldownKey = replenishmentCooldownKey(depotId, productId);
      long lastTriggerTick = lastDemandDrivenReplenishmentTickByProduct.getOrDefault(cooldownKey, Long.MIN_VALUE / 4);
      int currentStock = inventoryService.getAvailableStock(depotId, productId);
      int pendingDemand = pendingDemandForProduct(depotId, productId);
      int baseline = Math.max(
          Math.max(
              inventoryService.getBaselineStock(depotId, productId),
              inventoryService.getTotalStock(depotId, productId)),
          pendingDemand);
      if (baseline <= 0) {
        continue;
      }
      // RULE: s-S stock policy — reorder point s = 30% of baseline, target S = 80% of
      // baseline.
      // Tez isterleri uyarınca DispatchConstants içindeki eşikler kullanılır
      int reorderPoint = DispatchConstants.AUTO_REPLENISH_TRIGGER;
      int targetLevel = DispatchConstants.AUTO_REPLENISH_TARGET;
      boolean emergency = currentStock <= 0;
      if (currentStock > reorderPoint) {
        continue;
      }
      // RULE: Incoming stock counted — use getIncomingQuantity() to avoid
      // double-ordering.
      // Request quantity = (baseline * 0.80) - (currentStock + incomingQuantity)
      int incomingQuantity = replenishmentService.getIncomingQuantity(depotId, productId);
      int effectiveGap = targetLevel - (currentStock + incomingQuantity);
      if (effectiveGap <= 0) {
        // Incoming stock already covers the target — skip.
        continue;
      }
      int activeReplenishmentQuantity = activeReplenishmentQuantityForProduct(depotId, productId);
      if (activeReplenishmentQuantity >= effectiveGap) {
        continue;
      }
      if (lastTriggerTick == currentTick) {
        continue;
      }
      if (!emergency && isDemandDrivenReplenishmentCoolingDown(depotId, productId)) {
        continue;
      }
      // RULE: Main depot must have sufficient stock before creating a CREATED
      // request.
      if (!inventoryService.hasEnoughAvailable(mainDepot.getId(),
          List.of(new OrderLine(productId, 1, product.getUnitVolume())))) {
        eventLogService.record(
            currentTick,
            "REPLENISHMENT_SKIPPED",
            "Ana depo stoku yetersiz",
            depotId + " deposu icin " + productId + " urununde ana depoda yeterli stok yok; talep olusturulamadi.",
            "REPLENISHMENT",
            depotId + ":" + productId);
        continue;
      }

      int requestQuantity = computeDemandDrivenRequestQuantity(depotId, productId, currentStock, targetLevel,
          incomingQuantity, activeReplenishmentQuantity);
      if (requestQuantity <= 0) {
        continue;
      }

      OrderLine requestedLine = new OrderLine(productId, requestQuantity, product.getUnitVolume());
      ReplenishmentRequest request = replenishmentService.create(mainDepot.getId(), depotId, List.of(requestedLine),
          currentTick);
      String policyNote = emergency
          ? "Acil replenishment: stok sifira dustugu icin cooldown atlandi."
          : String.format(java.util.Locale.US,
              "(s,S) politikasi: mevcut=%d, yolda=%d, hedef=%d (baseline*0.80), talep=%d",
              currentStock, incomingQuantity, targetLevel, requestQuantity);
      request.annotateDecision(
          policyNote,
          "Main depo -> " + depotId,
          requestedLine.getProductId() + " x" + requestedLine.getQuantity(),
          "Uygun arac secimi ve rota skorlama asamasi bekleniyor.",
          0.0,
          0.0);
      activeReplenishments.put(request.getId(), request);
      replenishmentPlanDirty = true;
      lastDemandDrivenReplenishmentTickByProduct.put(cooldownKey, currentTick);
      eventLogService.record(
          currentTick,
          "REPLENISHMENT_REQUESTED",
          "Replenishment talebi olustu",
          depotId + " deposu icin " + requestedLine.getProductId() + " x" + requestedLine.getQuantity()
              + " ikmal talebi acildi. (" + policyNote + ")",
          "REPLENISHMENT",
          request.getId());
    }
  }

  private boolean hasActiveReplenishmentForProduct(String depotId, String productId) {
    return activeReplenishments.values().stream()
        .filter(request -> request.getStatus() != ReplenishmentStatus.COMPLETED)
        .filter(request -> depotId.equals(request.getTargetDepotId()))
        .flatMap(request -> request.getItems().stream())
        .anyMatch(item -> productId.equals(item.getProductId()));
  }

  private boolean isDemandDrivenReplenishmentCoolingDown(String depotId, String productId) {
    long lastTriggerTick = lastDemandDrivenReplenishmentTickByProduct
        .getOrDefault(replenishmentCooldownKey(depotId, productId), Long.MIN_VALUE / 4);
    return currentTick - lastTriggerTick < REPLENISHMENT_COOLDOWN_TICKS;
  }

  private String replenishmentCooldownKey(String depotId, String productId) {
    return depotId + "::" + productId;
  }

  /**
   * Calculate optimal demand-driven replenishment quantity using advanced (s,S)
   * policy.
   * 
   * Advanced Formula:
   * - effectiveGap = S - (currentStock + incomingQuantity +
   * activeReplenishmentQuantity)
   * - requestQuantity = max(0, effectiveGap), capped by vehicle capacity
   * 
   * This method removed the hard limit MAX_DEMAND_DRIVEN_SHIPMENT_UNITS.
   * Instead, quantity is now determined by actual gap and vehicle capacity.
   * 
   * @param depotId                     Target depot for replenishment
   * @param productId                   Product to replenish
   * @param currentStock                Available stock at target depot (not
   *                                    reserved)
   * @param targetLevel                 S value (target stock level, typically
   *                                    baseline * 0.80)
   * @param incomingQuantity            Quantity already in transit from main
   *                                    depot
   * @param activeReplenishmentQuantity Quantity already requested but not yet in
   *                                    transit
   * @return Optimal quantity to request, or 0 if target is met
   */
  private int computeDemandDrivenRequestQuantity(
      String depotId,
      String productId,
      int currentStock,
      int targetLevel,
      int incomingQuantity,
      int activeReplenishmentQuantity) {

    // Validate inputs
    if (targetLevel <= 0 || currentStock < 0 || incomingQuantity < 0 || activeReplenishmentQuantity < 0) {
      return 0;
    }

    // Calculate effective gap considering all pending stock
    int totalPendingStock = incomingQuantity + activeReplenishmentQuantity;
    int effectiveGap = Math.max(0, targetLevel - (currentStock + totalPendingStock));

    if (effectiveGap <= 0) {
      return 0; // Already at or above target level
    }

    // Get vehicle capacity constraint for this product
    Product product = productCatalogService.findById(productId).orElse(null);
    if (product == null) {
      return 0; // Product not found
    }

    int maxVehicleCapacity = maxVehicleShipmentQuantity(product);
    if (maxVehicleCapacity <= 0) {
      eventLogService.record(
          currentTick,
          "REPLENISHMENT_SKIPPED",
          "Replenishment atlandi",
          depotId + " deposu icin " + productId
              + " urunu mevcut araçların kapasitesine sigmadigi icin talep olusturulmadi.",
          "REPLENISHMENT",
          depotId + ":" + productId);
      return 0;
    }

    // Apply vehicle capacity limit (removed MAX_DEMAND_DRIVEN_SHIPMENT_UNITS hard
    // limit)
    int requestQuantity = Math.min(effectiveGap, maxVehicleCapacity);

    return Math.max(0, requestQuantity);
  }

  private int activeReplenishmentQuantityForProduct(String depotId, String productId) {
    return activeReplenishments.values().stream()
        .filter(request -> request.getStatus() != ReplenishmentStatus.COMPLETED)
        .filter(request -> depotId.equals(request.getTargetDepotId()))
        .flatMap(request -> request.getItems().stream())
        .filter(item -> productId.equals(item.getProductId()))
        .mapToInt(OrderLine::getQuantity)
        .sum();
  }

  private int maxVehicleShipmentQuantity(Product product) {
    return vehicles.stream()
        .filter(vehicle -> vehicle.supportsAllCategories(Set.of(product.getCategory())))
        .filter(vehicle -> vehicle.supportsCapabilities(product.getRequiredCapabilities()))
        .mapToInt(vehicle -> product.getUnitVolume() <= 0.0
            ? 0
            : (int) Math.floor(vehicle.getCapacityVolume() / product.getUnitVolume()))
        .max()
        .orElse(0);
  }

  private int pendingDemandForProduct(String depotId, String productId) {
    return pendingOrders.stream()
        .filter(order -> depotId.equals(order.getMicroDepotNodeId()))
        .flatMap(order -> order.getUnreservedItems().stream())
        .filter(item -> item.getProductId().equals(productId))
        .mapToInt(OrderLine::getQuantity)
        .sum();
  }

  private void handleActiveReplenishments() {
    Depot mainDepot = mainDepot();
    if (mainDepot == null) {
      dispatchReadinessByVehicle.clear();
      return;
    }
    if (!replenishmentPlanDirty) {
      refreshQueuedDispatchReadiness(mainDepot);
      return;
    }
    replenishmentPlanDirty = false;

    List<Vehicle> planningCandidates = new ArrayList<>();
    for (Vehicle vehicle : vehicles) {
      if (vehiclesCompletedThisTick.contains(vehicle.getId())) {
        dispatchReadinessByVehicle.remove(vehicle.getId());
        continue;
      }
      // Thread-safety: protect concurrent access from approveDispatch() REST thread.
      approvalLock.lock();
      try {
        if (approvalPendingPlansByVehicle.containsKey(vehicle.getId())) {
          DeliveryPlan queuedPlan = refreshApprovalPendingPlanUnsafe(vehicle, mainDepot,
              approvalPendingPlansByVehicle.get(vehicle.getId()));
          long waitedTicks = computeWaitedTicks(vehicle, mainDepot);
          dispatchReadinessByVehicle.put(vehicle.getId(),
              buildDispatchReadiness(vehicle, queuedPlan, waitedTicks, true, true));
          emitCapacityBannerIfNeeded(vehicle, queuedPlan, true);
          continue;
        }
      } finally {
        approvalLock.unlock();
      }
      if (!isVehicleEligibleForPlanning(vehicle, mainDepot) || stagedPlansByVehicle.containsKey(vehicle.getId())) {
        dispatchReadinessByVehicle.remove(vehicle.getId());
        continue;
      }
      if (vehicle.getWaitingSinceTick() < 0 && vehicle.getLocationNodeId().equals(mainDepot.getNodeId())) {
        vehicle.setWaitingSinceTick(currentTick);
      }
      planningCandidates.add(vehicle);
    }

    // Dinamik Skor Güncelleme: Bekleyen araçların plan skorlarını aciliyet arttıkça
    // yenile.
    updateStagedPlanScores(mainDepot);

    List<Vehicle> availableVehicles = new ArrayList<>(planningCandidates);
    while (!availableVehicles.isEmpty()) {
      // Logistics rule: among idle vehicles, assign the globally cheapest actionable
      // plan first.
      VehiclePlanOption bestAction = selectBestActionableVehiclePlan(availableVehicles, mainDepot);
      if (bestAction == null) {
        break;
      }

      if (!bestAction.vehicle().getLocationNodeId().equals(mainDepot.getNodeId())) {
        stagePlanByRepositioning(bestAction.vehicle(), mainDepot, bestAction.plan());
        dispatchReadinessByVehicle.remove(bestAction.vehicle().getId());
      } else if (requireManualDispatchApproval && bestAction.awaitingApproval()) {
        queuePlanForApproval(bestAction.vehicle(), bestAction.plan(), bestAction.waitedTicks());
      } else {
        dispatchPlan(bestAction.vehicle(), bestAction.plan(), bestAction.waitedTicks());
        dispatchReadinessByVehicle.remove(bestAction.vehicle().getId());
      }
      availableVehicles.remove(bestAction.vehicle());
    }

    for (Vehicle vehicle : availableVehicles) {
      List<ReplenishmentRequest> pending = pendingRequestsForPlanning();
      if (pending.isEmpty()) {
        dispatchReadinessByVehicle.remove(vehicle.getId());
        continue;
      }
      VehiclePlanOption option = evaluateVehiclePlan(vehicle, mainDepot, pending);
      if (option == null) {
        dispatchReadinessByVehicle.remove(vehicle.getId());
        continue;
      }
      if (!option.readyToDispatch() && !option.awaitingApproval()) {
        annotateDeferredForConsolidation(vehicle, option.plan());
      }
      dispatchReadinessByVehicle.put(
          vehicle.getId(),
          buildDispatchReadiness(vehicle, option.plan(), option.waitedTicks(), option.readyToDispatch(),
              option.awaitingApproval()));
      emitCapacityBannerIfNeeded(vehicle, option.plan(), option.awaitingApproval());
    }
  }

  private void refreshQueuedDispatchReadiness(Depot mainDepot) {
    for (Vehicle vehicle : vehicles) {
      if (vehiclesCompletedThisTick.contains(vehicle.getId())) {
        dispatchReadinessByVehicle.remove(vehicle.getId());
        continue;
      }
      // Thread-safety: protect concurrent access from approveDispatch() REST thread.
      approvalLock.lock();
      try {
        DeliveryPlan queuedPlan = approvalPendingPlansByVehicle.get(vehicle.getId());
        if (queuedPlan == null) {
          continue;
        }
        long waitedTicks = computeWaitedTicks(vehicle, mainDepot);
        dispatchReadinessByVehicle.put(vehicle.getId(),
            buildDispatchReadiness(vehicle, queuedPlan, waitedTicks, true, true));
        emitCapacityBannerIfNeeded(vehicle, queuedPlan, true);
      } finally {
        approvalLock.unlock();
      }
    }
  }

  private VehiclePlanOption selectBestActionableVehiclePlan(List<Vehicle> vehiclesToEvaluate, Depot mainDepot) {
    List<ReplenishmentRequest> pending = pendingRequestsForPlanning();
    if (pending.isEmpty()) {
      return null;
    }
    return vehiclesToEvaluate.stream()
        .map(vehicle -> evaluateVehiclePlan(vehicle, mainDepot, pending))
        .filter(Objects::nonNull)
        .filter(option -> option.readyToDispatch() || (requireManualDispatchApproval && option.awaitingApproval()))
        .findFirst() // GREEDY: İlk uygun planı seçer.
        .orElse(null);
  }

  private VehiclePlanOption evaluateVehiclePlan(Vehicle vehicle, Depot mainDepot, List<ReplenishmentRequest> pending) {
    DeliveryPlan bestPlan = chooseBestPlanForVehicle(vehicle, mainDepot, pending);
    if (bestPlan == null) {
      return null;
    }
    long waitedTicks = computeWaitedTicks(vehicle, mainDepot);
    double projectedLoadFraction = vehicle.getCapacityVolume() <= 0 ? 0.0
        : bestPlan.totalVolume() / vehicle.getCapacityVolume();

    boolean containsManualRequest = bestPlan.requests().stream().anyMatch(ReplenishmentRequest::isManual);
    boolean isManualVehicle = vehicle.isDedicatedManual();
    boolean bypassThreshold = isManualVehicle || containsManualRequest;

    boolean minimumLoadReached = bypassThreshold || projectedLoadFraction >= 0.50;
    long maxWaitTicks = vehicle.getMaxWaitTicks();
    boolean maxWaitTimerElapsed = maxWaitTicks > 0L && waitedTicks >= maxWaitTicks;

    // RULE: Below 50% capacity, regular vehicles NEVER dispatch, regardless of wait
    // time.
    if (!bypassThreshold && projectedLoadFraction < 0.50) {
      return new VehiclePlanOption(
          vehicle,
          bestPlan,
          waitedTicks,
          projectedLoadFraction,
          false,
          maxWaitTimerElapsed,
          false,
          false,
          computeVehicleSelectionCost(vehicle, bestPlan));
    }

    // SENIOR STRATEGY PHASE 1: Global Dispatch Rules Overriding vehicle defaults
    // RULE A: Below 50% load -> never dispatch (handled above)
    // RULE B: 50% - 74% load -> dispatch only after 180 ticks (3h)
    // RULE C: 75% and above -> immediate dispatch

    boolean readyByLoadAndTime = false;
    if (projectedLoadFraction >= 0.75) {
      readyByLoadAndTime = true;
    } else if (projectedLoadFraction >= 0.50) {
      readyByLoadAndTime = (waitedTicks >= 180);
    }

    // Manual requests (insan müdahalesi) veya özel manuel araçlar her zaman
    // önceliklidir ve kuralları bypass edebilir
    boolean readyToDispatch = bypassThreshold || readyByLoadAndTime;

    // Eğer araç hazır değilse ama %50 doluluğa ulaştıysa (manuel onay bekleyebilir
    // modda ise)
    boolean awaitingApproval = !readyToDispatch && !bypassThreshold && projectedLoadFraction >= 0.50;

    return new VehiclePlanOption(
        vehicle,
        bestPlan,
        waitedTicks,
        projectedLoadFraction,
        minimumLoadReached,
        waitedTicks >= 180, // maxWaitReached
        readyToDispatch,
        awaitingApproval,
        computeVehicleSelectionCost(vehicle, bestPlan));
  }

  private List<ReplenishmentRequest> pendingRequestsForPlanning() {
    return activeReplenishments.values().stream()
        .filter(this::isRequestPendingPlanning)
        .sorted(Comparator.comparingLong(ReplenishmentRequest::getCreatedTick))
        .toList();
  }

  private boolean isVehicleEligibleForPlanning(Vehicle vehicle, Depot mainDepot) {
    // RULE: WAITING_FOR_APPROVAL vehicles must never receive a new assignment —
    // they are locked pending human action.
    if (vehicle.getStatus() == VehicleStatus.WAITING_FOR_APPROVAL) {
      return false;
    }
    // RULE: RETURNING vehicles are eligible so that a plan can be pre-staged for
    // their arrival.
    // They must be unloaded (currentLoad == 0) and not already on an active
    // delivery route.
    if (vehicle.getStatus() == VehicleStatus.RETURNING) {
      return vehicle.getCurrentLoad() == 0.0
          && !vehicleMovementService.hasActiveRoute(vehicle.getId());
    }
    // RULE: Standard eligibility — vehicle must be IDLE, empty, and without an
    // active route.
    return vehicle.getStatus() == VehicleStatus.IDLE
        && vehicle.getCurrentLoad() == 0.0
        && !vehicleMovementService.hasActiveRoute(vehicle.getId());
  }

  private boolean isRequestPendingPlanning(ReplenishmentRequest request) {
    return request.getStatus() == ReplenishmentStatus.CREATED && !plannedRequestIds.contains(request.getId());
  }

  private long computeWaitedTicks(Vehicle vehicle, Depot mainDepot) {
    if (mainDepot == null || !vehicle.getLocationNodeId().equals(mainDepot.getNodeId())) {
      return 0L;
    }
    long waitingSinceTick = vehicle.getWaitingSinceTick();
    return waitingSinceTick >= 0 ? Math.max(0L, currentTick - waitingSinceTick) : 0L;
  }

  private void annotateDeferredForConsolidation(Vehicle vehicle, DeliveryPlan plan) {
    String reason = String.format(
        java.util.Locale.US,
        "Planlanan yuk %d%% minimum doluluk esiginin altinda. Yeni talep birikmesi veya maksimum bekleme bekleniyor.",
        (int) Math.round(vehicle.getMinimumDepartureLoadFraction() * 100.0));
    String cargoSummary = plan.cargoSummary() + " | Konsolidasyon bekleniyor";
    plan.requests().forEach(request -> request.annotateDecision(
        reason,
        plan.routeSummary(),
        cargoSummary,
        plan.comparisonSummary(),
        plan.planBenefit(),
        plan.netScore()));
  }

  private DeliveryPlan chooseBestPlanForVehicle(Vehicle vehicle, Depot mainDepot, List<ReplenishmentRequest> pending) {
    // RULE: Candidate vehicles must be IDLE, LOADING, or RETURNING — filtered
    // upstream.
    long vMask = CapabilityMasks.vehicleCapabilityMask(vehicle.getCapabilities());
    List<ReplenishmentRequest> eligible = pending.stream()
        .filter(request -> vehicle.isDedicatedManual() == request.isManual()) // RULE: Match vehicle type to request
                                                                              // type
        .filter(request -> vehicle.canTakeLoad(request.getTotalVolume()))
        .filter(request -> CapabilityMasks.vehicleCoversRequiredMask(vMask, requiredMaskForReplenishment(request)))
        .filter(request -> supportsCategories(vehicle, request))
        .filter(request -> inventoryService.hasEnoughAvailable(mainDepot.getId(), request.getItems()))
        .limit(24)
        .toList();

    if (eligible.isEmpty()) {
      return null;
    }

    DeliveryPlan currentPlan = eligible.stream()
        .map(request -> buildPlan(vehicle, mainDepot, List.of(request)))
        .filter(Objects::nonNull)
        .min(Comparator.comparingDouble(DeliveryPlan::score)) // Minimise Score = operationalCost - planBenefit
        .orElse(null);
    if (currentPlan == null) {
      return null;
    }

    List<ReplenishmentRequest> remaining = new ArrayList<>(eligible);
    remaining.removeAll(currentPlan.requests());
    // RULE: Max 4 stops. Greedy expansion only when plan improves AND deviation
    // stays within 20%.
    while (!remaining.isEmpty() && currentPlan.stopPlans().size() < effectiveDeliveryStopLimit(vehicle)) {
      DeliveryPlan snapshot = currentPlan;
      DeliveryPlan bestCandidate = null;
      ReplenishmentRequest bestRequest = null;

      for (ReplenishmentRequest request : remaining) {
        if (snapshot.totalVolume() + request.getTotalVolume() > vehicle.getCapacityVolume()) {
          continue;
        }
        List<ReplenishmentRequest> candidateRequests = new ArrayList<>(snapshot.requests());
        candidateRequests.add(request);
        DeliveryPlan candidate = buildPlan(vehicle, mainDepot, candidateRequests);

        if (candidate == null) {
          continue;
        }

        // RULE: Manual vehicles bypass the route deviation check to maximize cargo
        // consolidation.
        if (!vehicle.isDedicatedManual() && !isWithinMultiStopDeviation(snapshot, candidate)) {
          continue;
        }

        // RULE: Regular vehicles only expand if it improves score.
        // Dedicated manual vehicles expand greedily to include ALL compatible requests
        // as long as they fit.
        if (vehicle.isDedicatedManual()) {
          // For manual vehicles, we just want the largest feasible set.
          // We use a high score weight for volume to force it to pick candidates that add
          // more requests.
          if (bestCandidate == null || candidate.totalVolume() > bestCandidate.totalVolume()
              || candidate.score() < bestCandidate.score()) {
            bestCandidate = candidate;
            bestRequest = request;
          }
          continue;
        }

        if (candidate.score() > snapshot.score()) {
          continue;
        }

        if (bestCandidate == null || candidate.score() < bestCandidate.score()) {
          bestCandidate = candidate;
          bestRequest = request;
        }
      }

      if (bestCandidate == null) {
        break;
      }
      currentPlan = bestCandidate;
      remaining.remove(bestRequest);
    }

    currentPlan = consolidateTowardsMinimumVolume(vehicle, mainDepot, currentPlan, pending);

    // --- Decision Reasoning (Human-in-the-Loop transparency) ---
    // Capture effectively-final alias: currentPlan was re-assigned in the while
    // loop above.
    final DeliveryPlan finalPlan = currentPlan;
    List<DeliveryPlan> singlePlans = finalPlan.requests().stream()
        .map(request -> buildPlan(vehicle, mainDepot, List.of(request)))
        .filter(Objects::nonNull)
        .toList();
    double singleStopTotalCost = singlePlans.stream().mapToDouble(DeliveryPlan::operationalCost).sum();
    boolean isMultiStop = finalPlan.requests().size() > 1;
    String comparisonSummary;
    double savingsPct = 0.0;
    if (isMultiStop && singleStopTotalCost > 0) {
      savingsPct = ((singleStopTotalCost - finalPlan.operationalCost()) / singleStopTotalCost) * 100.0;
      comparisonSummary = savingsPct > 0
          ? String.format(java.util.Locale.US,
              "Multi-stop plan secildi: alternatif tek-durak planlarla karsilastirildiginda toplam maliyet %.1f%% daha dusuk.",
              savingsPct)
          : "Multi-stop plan secildi: tek seferde birden fazla depo beslemek genel operasyon dengesini iyilestiriyor.";
    } else {
      comparisonSummary = "Single-stop daha verimli bulundu; ek durak maliyeti kazanimi asiyor.";
    }

    // Build rich structured decision reason: "[Araç-X] secildi. Gerekce: ..."
    double fuelMultiplier = fuelCostMultiplier(vehicle);
    String multiStopNote = isMultiStop
        ? String.format(java.util.Locale.US, " + Multi-stop bonusu (+%.0f).", 25.0)
        : " (Tek durak, multi-stop bonusu yok).";
    String alternativeNote = "";
    // Find cheapest alternative vehicle plan for comparison (excluding this
    // vehicle)
    java.util.Optional<DeliveryPlan> bestAlternative = vehicles.stream()
        .filter(alt -> !alt.getId().equals(vehicle.getId()))
        .filter(alt -> isVehicleEligibleForPlanning(alt, mainDepot))
        .map(alt -> buildPlan(alt, mainDepot, finalPlan.requests()))
        .filter(Objects::nonNull)
        .min(Comparator.comparingDouble(DeliveryPlan::score));
    if (bestAlternative.isPresent()) {
      double altCost = bestAlternative.get().operationalCost();
      double myOpCost = finalPlan.operationalCost();
      if (altCost > 0 && myOpCost > 0) {
        double efficiencyGain = ((altCost - myOpCost) / altCost) * 100.0;
        alternativeNote = String.format(java.util.Locale.US,
            " Alternatif araca gore %%%s %.1f%% daha verimli.",
            efficiencyGain >= 0 ? "" : "-",
            Math.abs(efficiencyGain));
      }
    }
    String decisionReason = String.format(java.util.Locale.US,
        "[%s] secildi. Gerekce: En dusuk yakit maliyeti (%.2f — %s) ve NetScore=%.2f (operasyon=%.2f, fayda=%.2f).%s%s",
        vehicle.getId(),
        fuelMultiplier,
        vehicle.getFuelType(),
        currentPlan.netScore(),
        currentPlan.operationalCost(),
        currentPlan.planBenefit(),
        multiStopNote,
        alternativeNote);

    return currentPlan.withDecision(decisionReason, comparisonSummary);
  }

  /**
   * Maliyet odakli greedy plan bazen minimum doluluk esiginin altinda kalir;
   * bekleyen taleplerden uygun olanlari
   * ekleyerek bu esige ulasmayi dener (durak ve kapasite sinirlarina uyarak).
   */
  private DeliveryPlan consolidateTowardsMinimumVolume(
      Vehicle vehicle,
      Depot mainDepot,
      DeliveryPlan currentPlan,
      List<ReplenishmentRequest> pending) {
    double targetVolume = vehicle.getCapacityVolume() * vehicle.getMinimumDepartureLoadFraction();
    DeliveryPlan plan = currentPlan;
    while (plan.totalVolume() < targetVolume - 1e-6 && plan.stopPlans().size() < effectiveDeliveryStopLimit(vehicle)) {
      final DeliveryPlan snapshot = plan;
      List<ReplenishmentRequest> candidates = pending.stream()
          .filter(request -> !snapshot.requests().contains(request))
          .filter(request -> snapshot.totalVolume() + request.getTotalVolume() <= vehicle.getCapacityVolume())
          .filter(request -> supportsCategories(vehicle, request))
          .filter(request -> supportsCapabilities(vehicle, request))
          .filter(request -> inventoryService.hasEnoughAvailable(mainDepot.getId(), request.getItems()))
          .sorted(Comparator.comparingDouble(ReplenishmentRequest::getTotalVolume).reversed())
          .toList();

      if (candidates.isEmpty()) {
        break;
      }

      ReplenishmentRequest addition = candidates.stream()
          .filter(request -> {
            List<ReplenishmentRequest> expanded = new ArrayList<>(snapshot.requests());
            expanded.add(request);
            DeliveryPlan expandedPlan = buildPlan(vehicle, mainDepot, expanded);
            return expandedPlan != null && isWithinMultiStopDeviation(snapshot, expandedPlan);
          })
          .findFirst()
          .orElse(null);
      if (addition == null) {
        break;
      }
      List<ReplenishmentRequest> expandedRequests = new ArrayList<>(plan.requests());
      expandedRequests.add(addition);
      DeliveryPlan expanded = buildPlan(vehicle, mainDepot, expandedRequests);
      if (expanded == null) {
        break;
      }
      plan = expanded;
    }
    return plan;
  }

  private DeliveryPlan buildPlan(Vehicle vehicle, Depot mainDepot, List<ReplenishmentRequest> requests) {
    if (requests.isEmpty()) {
      return null;
    }
    double totalVolume = requests.stream().mapToDouble(ReplenishmentRequest::getTotalVolume).sum();
    // Logistics rule: no order group may exceed 100% of vehicle capacity.
    if (totalVolume > vehicle.getCapacityVolume() + 1e-6) {
      return null;
    }
    List<MergedStop> mergedStops = mergeRequestsByDepot(requests, vehicle);
    // Logistics rule: preserve existing per-vehicle stop limits, but also enforce a
    // hard trip cap.
    if (mergedStops.isEmpty() || mergedStops.size() > effectiveDeliveryStopLimit(vehicle)) {
      return null;
    }

    List<MergedStop> bestSequence = null;
    double minSequenceCost = Double.POSITIVE_INFINITY;
    List<List<MergedStop>> permutations = generatePermutations(mergedStops);

    for (List<MergedStop> sequenceCandidate : permutations) {
      double currentSequenceCost = 0.0;
      String tempNode = mainDepot.getNodeId();
      for (MergedStop stop : sequenceCandidate) {
        currentSequenceCost += scoreToTarget(vehicle, tempNode, depotNode(stop.targetDepotId()));
        tempNode = depotNode(stop.targetDepotId());
      }
      if (currentSequenceCost < minSequenceCost) {
        minSequenceCost = currentSequenceCost;
        bestSequence = sequenceCandidate;
      }
    }

    if (bestSequence == null) {
      return null;
    }

    List<String> route = new ArrayList<>();
    route.add(mainDepot.getNodeId());
    List<VehicleMovementService.StopPlan> stopPlans = new ArrayList<>();
    List<OrderLine> mergedItems = mergeItems(
        requests.stream().flatMap(request -> request.getItems().stream()).toList());
    String currentNode = mainDepot.getNodeId();
    Set<String> visitedDepots = new HashSet<>();

    for (MergedStop nextStop : bestSequence) {
      List<String> segment = computeVehicleRoute(vehicle, currentNode, depotNode(nextStop.targetDepotId()));
      if (segment.isEmpty()) {
        return null; // Should not happen if scoreToTarget was finite
      }
      route.addAll(segment.subList(1, segment.size()));
      currentNode = depotNode(nextStop.targetDepotId());
      visitedDepots.add(nextStop.targetDepotId());
      stopPlans.add(new VehicleMovementService.StopPlan(
          nextStop.targetDepotId(),
          currentNode,
          nextStop.requestIds(),
          nextStop.mergedItems(),
          nextStop.totalVolume(),
          computeUnloadTicks(vehicle, nextStop.totalVolume())));
    }

    double deliveryDistance = computeDistance(route);
    List<String> repositionRoute = computeVehicleRoute(vehicle, vehicle.getLocationNodeId(), mainDepot.getNodeId());
    if (repositionRoute.isEmpty()) {
      return null;
    }
    double repositionDistance = computeDistance(repositionRoute);

    // --- Tam Tur (Round Trip) Hesaplaması: Gidiş + Dönüş ---
    List<String> returnRoute = computeVehicleRoute(vehicle, currentNode, mainDepot.getNodeId());
    double returnDistance = returnRoute.isEmpty() ? 0.0 : computeDistance(returnRoute);
    double totalRoundTripDistance = deliveryDistance + repositionDistance + returnDistance;

    // --- MALIYET HESABI: YAKIT + GISE (TOLL) ---
    double fuelCost = totalRoundTripDistance * vehicle.getCostPerKm();
    double totalTollCost = 0.0;
    
    // Tüm etaplardaki gişe ücretlerini topla
    for (int i = 0; i < route.size() - 1; i++) {
      Edge e = graph.getEdge(route.get(i), route.get(i + 1));
      if (e != null) totalTollCost += e.getTollCost();
    }
    for (int i = 0; i < repositionRoute.size() - 1; i++) {
      Edge e = graph.getEdge(repositionRoute.get(i), repositionRoute.get(i + 1));
      if (e != null) totalTollCost += e.getTollCost();
    }
    for (int i = 0; i < returnRoute.size() - 1; i++) {
      Edge e = graph.getEdge(returnRoute.get(i), returnRoute.get(i + 1));
      if (e != null) totalTollCost += e.getTollCost();
    }
    
    if (vehicle.getCapacityVolume() >= HEAVY_VEHICLE_CAPACITY_THRESHOLD) {
      totalTollCost *= HEAVY_VEHICLE_TOLL_MULTIPLIER;
    }
    
    double totalOperationalCost = fuelCost + totalTollCost;

    // --- FAYDA VE SKOR HESABI ---
    double totalCargoValue = 0.0;
    for (ReplenishmentRequest req : requests) {
      double reqVal = computeTotalCargoFinancialValue(req.getItems());
      req.setTotalFinancialValue(reqVal);
      totalCargoValue += reqVal;
    }

    double bcrRatio = totalCargoValue / Math.max(1.0, totalOperationalCost);
    double loadFraction = vehicle.getCapacityVolume() <= 0 ? 0.0 : totalVolume / vehicle.getCapacityVolume();
    double capacityScore = loadFraction * 1000.0; 

    double urgencyScore = 0.0;
    final double ALPHA = 0.01;
    for (ReplenishmentRequest req : requests) {
      long waitedTicks = Math.max(0, currentTick - req.getCreatedTick());
      urgencyScore += Math.pow(waitedTicks, 2) * ALPHA;
      if (isUrgentRequest(req)) urgencyScore += 300.0;
    }

    double planBenefit = capacityScore + urgencyScore;
    double netScore = planBenefit; 
    double score = -netScore;

    double routeCost = ROUTE_COST_WEIGHT * (computeRouteCost(route, vehicle) + computeRouteCost(repositionRoute, vehicle));
    long loadingTicks = computeLoadTicks(vehicle, totalVolume);
    long unloadingTicks = stopPlans.stream().mapToLong(VehicleMovementService.StopPlan::unloadTicks).sum();
    long missionTicks = loadingTicks + unloadingTicks + computeTravelTicks(route, vehicle) + computeTravelTicks(repositionRoute, vehicle);

    String routeStr = stopPlans.stream().map(VehicleMovementService.StopPlan::depotId).collect(Collectors.joining(" -> "));
    long departureTick = currentTick + loadingTicks;
    long idealTravelTicks = Math.round(totalRoundTripDistance / (vehicle.getAverageSpeedKmPerHour() / 60.0));
    long idealArrivalTick = departureTick + idealTravelTicks;
    
    String routeSummary = String.format(java.util.Locale.US,
        "Gorev: Main -> %s -> Main. (Mesafe: %.1f km | İdeal Sure: %d dk) [Kalkis: %s, Varis: %s]", 
        routeStr, totalRoundTripDistance, idealTravelTicks, formatTickToTime(departureTick), formatTickToTime(idealArrivalTick));

    StringBuilder cargoValueDetail = new StringBuilder();
    for (OrderLine item : mergedItems) {
      Product p = productCatalogService.findById(item.getProductId()).orElse(null);
      if (p != null) {
        double lineVal = p.getFinancialValue() * item.getQuantity();
        cargoValueDetail.append(String.format("%s*%d=%.0f TL, ", item.getProductId(), item.getQuantity(), lineVal));
      }
    }
    if (cargoValueDetail.length() > 2) cargoValueDetail.setLength(cargoValueDetail.length() - 2);

    String cargoSummary = String.format(java.util.Locale.US, "Yuk: %.1f m3 | Deger: [%s] Toplam: %.0f TL", totalVolume, cargoValueDetail.toString(), totalCargoValue);

    String comparisonSummary = String.format(java.util.Locale.US,
        "Tam Misyon Maliyeti (Gidis-Donus): %.1f TL | Verimlilik (ROI): %.1fx | LES: %.0f Puan",
        totalOperationalCost, bcrRatio, planBenefit);

    // Veri Tutarliligi: Donus yolunda da ayni ROI'yi gosterebilmek icin araca kaydet
    vehicle.setCurrentMissionTotalCost(totalOperationalCost);
    vehicle.setCurrentMissionCargoValue(totalCargoValue);
    vehicle.setCurrentMissionBcrRatio(bcrRatio);

    if (!inventoryService.hasEnoughAvailable(mainDepot.getId(), mergedItems)) {
      return null;
    }

    return new DeliveryPlan(
        "plan-" + vehicle.getId() + "-" + requests.stream().map(ReplenishmentRequest::getId).collect(Collectors.joining("-")),
        List.copyOf(requests), route, List.copyOf(stopPlans), mergedItems,
        totalRoundTripDistance, totalOperationalCost, totalVolume, score,
        loadingTicks, missionTicks, requests.stream().anyMatch(this::isUrgentRequest),
        deliveryDistance, repositionDistance, planBenefit, netScore, routeCost,
        0.0, 0.0, 0.0, 0.0, 0.0, 0.0, null, null, routeSummary, cargoSummary);
  }

  private long requiredMaskForReplenishment(ReplenishmentRequest request) {
    long mask = 0L;
    for (OrderLine line : request.getItems()) {
      Product p = productCatalogService.findById(line.getProductId()).orElse(null);
      if (p != null) {
        mask |= CapabilityMasks.productCapabilityMask(p.getRequiredCapabilities());
      }
    }
    return mask;
  }

  private void emitCapacityBannerIfNeeded(Vehicle vehicle, DeliveryPlan plan, boolean awaitingApproval) {
    if (!awaitingApproval || vehicle.getCapacityVolume() <= 0) {
      return;
    }
    double lf = plan.totalVolume() / vehicle.getCapacityVolume();
    if (lf >= 0.5 && lf < 0.75 && capacityBannerEmittedForVehicle.add(vehicle.getId())) {
      eventLogService.record(
          currentTick,
          "DISPATCH_CAPACITY_ALERT",
          "Kapasite uyarisi",
          String.format(java.util.Locale.US,
              "Dikkat: %s Araci %%50+ Kapasite Doldurdu Cikis Icin Onay Bekliyor!",
              vehicle.getId()),
          "VEHICLE",
          vehicle.getId());
    }
  }

  private boolean supportsCategories(Vehicle vehicle, ReplenishmentRequest request) {
    Set<ProductCategory> categories = request.getItems().stream()
        .map(item -> productCatalogService.findById(item.getProductId()).map(Product::getCategory)
            .orElse(ProductCategory.GENERAL))
        .collect(Collectors.toSet());
    return vehicle.supportsAllCategories(categories);
  }

  private boolean supportsCapabilities(Vehicle vehicle, ReplenishmentRequest request) {
    Set<ProductCapability> capabilities = request.getItems().stream()
        .flatMap(item -> productCatalogService.findById(item.getProductId()).stream())
        .flatMap(product -> product.getRequiredCapabilities().stream())
        .collect(Collectors.toSet());
    return vehicle.supportsCapabilities(capabilities);
  }

  private double scoreToTarget(Vehicle vehicle, String fromNode, String targetNode) {
    List<String> route = computeVehicleRoute(vehicle, fromNode, targetNode);
    if (route.isEmpty()) {
      return Double.POSITIVE_INFINITY;
    }
    return routingService.computePathCost(graph, route, routingProfileFor(vehicle));
  }

  private double computeVehicleSelectionCost(Vehicle vehicle, DeliveryPlan plan) {
    // Logistics rule: vehicle choice is based on total route distance cost + fuel
    // cost,
    // not just the nearest idle vehicle.
    return plan.routeCost();
  }

  private boolean isWithinMultiStopDeviation(DeliveryPlan basePlan, DeliveryPlan candidatePlan) {
    if (basePlan == null || candidatePlan == null || basePlan.stopPlans().isEmpty()) {
      return true;
    }
    if (candidatePlan.stopPlans().size() <= basePlan.stopPlans().size()) {
      return true;
    }
    double totalValue = totalCargoFinancialValue(candidatePlan.items());
    // LISANS TEZI ONAYLI FORMUL (Korumali Sapma Limiti):
    // 1. Ana rota (git-gel dahil) mesafesinin %35'i kadar baz sapma esnekligi.
    // 2. Kargo degerine gore ekstra sapma hakki (Anormal degerleri onlemek icin
    // Maks. 60 km ile sinirlandirildi).
    // 3. DispatchConstants içindeki sabit tolerans eklenir.
    double valueBonusKm = Math.min(60.0, totalValue / DispatchConstants.CARGO_VALUE_DIVISOR);
    double maxBypassKm = (basePlan.deliveryDistanceKm() * DispatchConstants.MULTI_STOP_DEVIATION_RATIO) + valueBonusKm
        + DispatchConstants.BASE_BYPASS_KM;
    return candidatePlan.deliveryDistanceKm() <= basePlan.deliveryDistanceKm() + maxBypassKm + 1e-6;
  }

  private <T> List<List<T>> generatePermutations(List<T> original) {
    if (original.isEmpty()) {
      return List.of(List.of());
    }
    List<List<T>> result = new ArrayList<>();
    T first = original.get(0);
    List<T> rest = original.subList(1, original.size());
    for (List<T> subPermutation : generatePermutations(rest)) {
      for (int i = 0; i <= subPermutation.size(); i++) {
        List<T> next = new ArrayList<>(subPermutation);
        next.add(i, first);
        result.add(next);
      }
    }
    return result;
  }

  private double totalCargoFinancialValue(List<OrderLine> lines) {
    double sum = 0.0;
    for (OrderLine line : lines) {
      Product p = productCatalogService.findById(line.getProductId()).orElse(null);
      if (p != null) {
        sum += p.getFinancialValue() * line.getQuantity();
      }
    }
    return sum;
  }

  private void updateStagedPlanScores(Depot mainDepot) {
    // 1. Onay bekleyen planların skorlarını güncelle
    approvalPendingPlansByVehicle.forEach((vehicleId, plan) -> {
      Vehicle v = vehicles.stream().filter(vch -> vch.getId().equals(vehicleId)).findFirst().orElse(null);
      if (v != null) {
        DeliveryPlan updated = buildPlan(v, mainDepot, plan.requests());
        if (updated != null) {
          approvalPendingPlansByVehicle.put(vehicleId, updated);
        }
      }
    });

    // 2. Dispatch bekleyen (Ready) planların skorlarını güncelle
    dispatchReadinessByVehicle.forEach((vehicleId, snapshot) -> {
      Vehicle v = vehicles.stream().filter(vch -> vch.getId().equals(vehicleId)).findFirst().orElse(null);
      if (v != null) {
        DeliveryPlan currentPlan = stagedPlansByVehicle.get(vehicleId);
        if (currentPlan != null) {
          DeliveryPlan updated = buildPlan(v, mainDepot, currentPlan.requests());
          if (updated != null) {
            stagedPlansByVehicle.put(vehicleId, updated);
            // Snapshot'ı güncelle ki frontend'de skor artsın
            dispatchReadinessByVehicle.put(vehicleId, buildDispatchReadiness(v, updated, snapshot.waitTicks(),
                snapshot.readyToDispatch(), snapshot.awaitingApproval()));
          }
        }
      }
    });
  }

  private double averageTrafficFactorOnRoute(List<String> route) {
    if (route == null || route.size() < 2) {
      return 1.0;
    }
    double sum = 0.0;
    int n = 0;
    for (int i = 0; i < route.size() - 1; i++) {
      Edge e = graph.getEdge(route.get(i), route.get(i + 1));
      if (e != null) {
        sum += e.getTrafficFactor();
        n++;
      }
    }
    return n == 0 ? 1.0 : sum / n;
  }

  private int effectiveDeliveryStopLimit(Vehicle vehicle) {
    // Dedicated manual vehicles can use virtually unlimited stops per trip.
    if (vehicle.isDedicatedManual())
      return 99;
    return Math.max(1, Math.min(vehicle.getMaxStopCount(), MAX_STOPS_PER_TRIP));
  }

  private RoutingService.RoutingProfile routingProfileFor(Vehicle vehicle) {
    double tollWeightMultiplier = vehicle.getCapacityVolume() >= HEAVY_VEHICLE_CAPACITY_THRESHOLD
        ? HEAVY_VEHICLE_TOLL_MULTIPLIER
        : 1.0;
    return new RoutingService.RoutingProfile(
        vehicle.getCostPerKm(),
        1.0,
        tollWeightMultiplier,
        fuelCostMultiplier(vehicle));
  }

  private double fuelCostMultiplier(Vehicle vehicle) {
    // Jüri için maliyetleri araç kartındaki costPerKm değerleriyle eşitle
    return vehicle.getCostPerKm();
  }

  private List<String> computeVehicleRoute(Vehicle vehicle, String fromNode, String toNode) {
    return routingService.computeShortestPath(graph, fromNode, toNode, routingProfileFor(vehicle));
  }

  private void stagePlanByRepositioning(Vehicle vehicle, Depot mainDepot, DeliveryPlan plan) {
    List<String> routeToMain = computeVehicleRoute(vehicle, vehicle.getLocationNodeId(), mainDepot.getNodeId());
    if (routeToMain.isEmpty()) {
      return;
    }
    markRequestsAsPlanned(plan);
    reserveRequestsForVehicle(plan, vehicle);
    stagedPlansByVehicle.put(vehicle.getId(), plan);
    double distance = computeDistance(routeToMain);
    double cost = computeRouteCost(routeToMain, vehicle);
    metricsEventHandler.recordDistance(vehicle.getId(), distance);
    vehicle.setStatus(VehicleStatus.RETURNING);
    vehicle.setWaitingSinceTick(-1L);
    vehicleMovementService.assignRoute(
        vehicle.getId(),
        "position-" + vehicle.getId() + "-" + currentTick,
        routeToMain,
        buildSegmentPlans(routeToMain, vehicle),
        List.of(),
        currentTick,
        0.0,
        "POSITIONING",
        distance,
        cost,
        0.0,
        0.0,
        0L,
        0L,
        "Arac once ana depoya reposition edildi.",
        "Arac yeni gorev icin ana depoya donuyor.",
        "Mevcut konum -> Main depo",
        "Bos arac",
        "Ana depoya donus tamamlaninca ikmal gorevi baslatilacak.",
        cost,
        0.0,
        -cost,
        computeTravelTicks(routeToMain, vehicle),
        distance,
        0.0,
        cost,
        0.0,
        0.0,
        0.0,
        0.0,
        0.0,
        0.0);
  }

  private void queuePlanForApproval(Vehicle vehicle, DeliveryPlan plan, long waitedTicks) {
    DeliveryPlan latestPlan = refreshPlanForDispatch(vehicle, mainDepot(), plan);
    approvalLock.lock();
    try {
      if (!approvalPendingPlansByVehicle.containsKey(vehicle.getId())) {
        markRequestsAsPlanned(latestPlan);
        reserveRequestsForVehicle(latestPlan, vehicle);
        // Set WAITING_FOR_APPROVAL status so the vehicle does not depart autonomously.
        vehicle.setStatus(VehicleStatus.WAITING_FOR_APPROVAL);
        approvalWaitingSinceTick.put(vehicle.getId(), currentTick);
        long approvalWindow = Math.max(5L, 180L - waitedTicks);
        approvalTimeoutLimitTicks.put(vehicle.getId(), approvalWindow);
        String approvalTitle = "Arac cikis onayi bekliyor";
        String approvalDetail = vehicle.getId() + " araci " + latestPlan.routeSummary()
            + " icin kullanici onayi bekliyor. " + latestPlan.decisionReason();
        eventLogService.record(
            currentTick,
            "VEHICLE_READY_FOR_APPROVAL",
            approvalTitle,
            approvalDetail,
            "VEHICLE",
            vehicle.getId());
      } else {
        reserveDecisionDetails(latestPlan);
      }
      approvalPendingPlansByVehicle.put(vehicle.getId(), latestPlan);
    } finally {
      approvalLock.unlock();
    }
    emitCapacityBannerIfNeeded(vehicle, latestPlan, true);
    dispatchReadinessByVehicle.put(vehicle.getId(),
        buildDispatchReadiness(vehicle, latestPlan, waitedTicks, true, true));
  }

  private DispatchReadinessSnapshot buildDispatchReadiness(
      Vehicle vehicle,
      DeliveryPlan plan,
      long waitedTicks,
      boolean readyToDispatch,
      boolean awaitingApproval) {
    double loadFraction = vehicle.getCapacityVolume() <= 0 ? 0.0 : plan.totalVolume() / vehicle.getCapacityVolume();
    double minimumLoadFraction = 0.50;
    double immediateLoadFraction = 0.75;

    boolean containsManualRequest = plan.requests().stream().anyMatch(ReplenishmentRequest::isManual);
    boolean isManualVehicle = vehicle.isDedicatedManual();
    boolean bypassThreshold = isManualVehicle || containsManualRequest;

    boolean readyToDispatchOverride = false;
    if (bypassThreshold) {
      readyToDispatchOverride = true;
    } else if (loadFraction >= 0.75) {
      readyToDispatchOverride = true;
    } else if (loadFraction >= 0.50) {
      readyToDispatchOverride = (waitedTicks >= 180);
    } else {
      readyToDispatchOverride = false;
    }

    boolean minimumLoadReached = bypassThreshold || loadFraction >= 0.50;
    boolean immediateDispatchReached = bypassThreshold || loadFraction >= 0.75;
    long maxWaitTicks = 180L;
    boolean maxWaitReached = waitedTicks >= 180L;

    long firstRequestTick = plan.requests().stream().mapToLong(ReplenishmentRequest::getCreatedTick).min().orElse(0L);
    long lastRequestUpdateTick = plan.requests().stream().mapToLong(ReplenishmentRequest::getLastUpdatedTick).max()
        .orElse(firstRequestTick);

    return new DispatchReadinessSnapshot(
        vehicle.getId(),
        plan.referenceId(),
        plan.requests().stream().map(ReplenishmentRequest::getId).toList(),
        plan.requests().stream().map(ReplenishmentRequest::getTargetDepotId).distinct().toList(),
        plan.totalVolume(),
        vehicle.getCapacityVolume(),
        loadFraction,
        minimumLoadFraction,
        immediateLoadFraction,
        minimumLoadReached,
        waitedTicks,
        180L, // Max wait ticks for Phase 1
        maxWaitReached,
        plan.urgent(),
        readyToDispatchOverride, // Use our override
        awaitingApproval,
        firstRequestTick,
        lastRequestUpdateTick,
        plan.routeSummary(),
        plan.cargoSummary(),
        buildDispatchDecisionReason(
            loadFraction,
            readyToDispatch,
            minimumLoadReached,
            immediateDispatchReached,
            maxWaitReached,
            awaitingApproval,
            minimumLoadFraction,
            immediateLoadFraction),
        plan.comparisonSummary(),
        plan.stopPlans().size(),
        plan.missionTicks(),
        plan.deliveryDistanceKm(),
        plan.repositionDistanceKm(),
        plan.operationalCost(),
        plan.planBenefit(),
        plan.netScore(),
        plan.routeCost(),
        plan.handlingCost(),
        plan.timeCost(),
        plan.extraStopCost(),
        plan.delayPenalty(),
        plan.stockoutPenalty(),
        plan.opportunityCost());
  }

  private String buildDispatchDecisionReason(
      double loadFraction,
      boolean readyToDispatch,
      boolean minimumLoadReached,
      boolean immediateDispatchReached,
      boolean maxWaitReached,
      boolean awaitingApproval,
      double minimumLoadFraction,
      double immediateLoadFraction) {
    if (awaitingApproval) {
      return "Arac cikisa hazir. Kullanici onayi beklendigi icin dispatch beklemede.";
    }
    if (!minimumLoadReached) {
      if (maxWaitReached && readyToDispatch) {
        return String.format(
            java.util.Locale.US,
            "Doluluk %.0f%% (minimum %.0f%% altinda); maksimum bekleme suresi doldugu icin sevkiyat yine de baslatildi.",
            loadFraction * 100.0,
            minimumLoadFraction * 100.0);
      }
      return String.format(
          java.util.Locale.US,
          "Doluluk %.0f%% seviyesinde. %.0f%% minimumun altinda oldugu icin talep birikmesi veya maksimum bekleme bekleniyor.",
          loadFraction * 100.0,
          minimumLoadFraction * 100.0);
    }
    if (readyToDispatch) {
      if (immediateDispatchReached) {
        return String.format(java.util.Locale.US, "Doluluk %.0f%% seviyesine ulasti; arac aninda dispatch edildi.",
            loadFraction * 100.0);
      }
      if (maxWaitReached) {
        return String.format(java.util.Locale.US,
            "Doluluk %.0f%% bandinda kaldi; maksimum bekleme suresi doldugu icin dispatch edildi.",
            loadFraction * 100.0);
      }
      return "Arac cikisa hazir.";
    }
    return String.format(
        java.util.Locale.US,
        "Doluluk %.0f%% seviyesinde. %.0f%%-%.0f%% bandindaki arac maksimum bekleme suresine kadar bekliyor.",
        loadFraction * 100.0,
        minimumLoadFraction * 100.0,
        immediateLoadFraction * 100.0);
  }

  /**
   * @return false ana depoda (reposition/onay sonrasi baska araclar nedeniyle)
   *         yetersiz stok; arac IDLE birakilir,
   *         staged/onay talepleri CREATED'a doner. true ise sevkiyat baslatildi.
   */
  private boolean dispatchPlan(Vehicle vehicle, DeliveryPlan plan, long waitedTicks) {
    Depot mainDepot = mainDepot();
    if (mainDepot == null) {
      return false;
    }
    markRequestsAsPlanned(plan);
    if (!inventoryService.hasEnoughAvailable(mainDepot.getId(), plan.items())) {
      eventLogService.record(
          currentTick,
          "DISPATCH_SKIPPED",
          "Stok yetersiz, dispatch atlandi",
          vehicle.getId() + " icin stok tukendi, plan iptal edildi.",
          "VEHICLE",
          vehicle.getId());
      plan.requests().forEach(ReplenishmentRequest::resetToCreated);
      releasePlannedRequests(plan);
      stagedPlansByVehicle.remove(vehicle.getId());
      approvalPendingPlansByVehicle.remove(vehicle.getId());
      dispatchReadinessByVehicle.remove(vehicle.getId());
      replenishmentPlanDirty = true;
      if (vehicle.getLocationNodeId().equals(mainDepot.getNodeId())) {
        vehicle.setStatus(VehicleStatus.IDLE);
        vehicle.setWaitingSinceTick(currentTick);
      }
      return false;
    }
    inventoryService.consume(mainDepot.getId(), plan.items());
    recordDepotStockChange(mainDepot.getId(), plan.items(), -1);
    capacityBannerEmittedForVehicle.remove(vehicle.getId());
    vehicle.addLoad(plan.totalVolume());
    vehicle.setStatus(plan.loadingTicks() > 0 ? VehicleStatus.LOADING : VehicleStatus.EN_ROUTE);
    double loadFraction = vehicle.getCapacityVolume() <= 0 ? 0.0 : plan.totalVolume() / vehicle.getCapacityVolume();
    if (requireManualDispatchApproval && loadFraction > 0.75) {
      eventLogService.record(
          currentTick,
          "VEHICLE_AUTONOMOUS_FULL_DISPATCH",
          "Tam kapasite otonom cikis",
          String.format(java.util.Locale.US,
              "Arac Tam Kapasite Otonom Cikis Yapti: [%s] doluluk %.0f%% (> 75%%).",
              vehicle.getId(), loadFraction * 100.0),
          "VEHICLE",
          vehicle.getId());
    }
    String departureReason = loadFraction >= immediateDepartureLoadFraction(vehicle)
        ? String.format(java.util.Locale.US, "Arac %.0f doluluk ile aninda dispatch edildi.", loadFraction * 100.0)
        : String.format(java.util.Locale.US, "Arac %.0f dolulukta maksimum bekleme suresi doldugu icin hareket etti.",
            loadFraction * 100.0);
    reserveRequestsForVehicle(plan, vehicle);
    plan.requests().forEach(ReplenishmentRequest::markInTransit);
    releasePlannedRequests(plan);
    eventLogService.record(currentTick, "REPLENISHMENT_DISPATCHED", "Ikmal rotasi baslatildi",
        plan.routeSummary() + ". " + plan.comparisonSummary(), "VEHICLE", vehicle.getId());
    long travelTicks = buildSegmentPlans(plan.route(), vehicle).stream()
        .mapToLong(VehicleMovementService.SegmentPlan::travelTicks).sum();
    long actualDepartureTick = currentTick + plan.loadingTicks();
    long estimatedArrivalTick = actualDepartureTick + travelTicks;

    String updatedRouteSummary = plan.routeSummary();
    if (updatedRouteSummary.contains("[T.")) {
      updatedRouteSummary = updatedRouteSummary.substring(0, updatedRouteSummary.indexOf("[T."))
          + String.format("[Kalkis: %s, T. Varis: %s]", formatTickToTime(actualDepartureTick),
              formatTickToTime(estimatedArrivalTick));
    }

    vehicleMovementService.assignRoute(
        vehicle.getId(),
        plan.referenceId(),
        plan.route(),
        buildSegmentPlans(plan.route(), vehicle),
        plan.stopPlans(),
        currentTick,
        plan.totalVolume(),
        "REPLENISHMENT",
        plan.deliveryDistanceKm(),
        plan.operationalCost(),
        vehicle.getCapacityVolume(),
        loadFraction,
        waitedTicks,
        plan.loadingTicks(),
        plan.decisionReason(),
        departureReason,
        updatedRouteSummary,
        plan.cargoSummary(),
        plan.comparisonSummary(),
        plan.operationalCost(),
        plan.planBenefit(),
        plan.netScore(),
        plan.missionTicks(),
        plan.deliveryDistanceKm(),
        plan.repositionDistanceKm(),
        plan.routeCost(),
        plan.handlingCost(),
        plan.timeCost(),
        plan.extraStopCost(),
        plan.delayPenalty(),
        plan.stockoutPenalty(),
        plan.opportunityCost());
    metricsEventHandler.recordDepotDispatch(mainDepot.getId());
    metricsEventHandler.recordDistance(vehicle.getId(), plan.deliveryDistanceKm());
    vehicle.setWaitingSinceTick(-1L);
    stagedPlansByVehicle.remove(vehicle.getId());
    approvalPendingPlansByVehicle.remove(vehicle.getId());
    dispatchReadinessByVehicle.remove(vehicle.getId());
    return true;
  }

  private void markRequestsAsPlanned(DeliveryPlan plan) {
    plan.requests().forEach(request -> plannedRequestIds.add(request.getId()));
  }

  private void releasePlannedRequests(DeliveryPlan plan) {
    plan.requests().forEach(request -> plannedRequestIds.remove(request.getId()));
  }

  private void reserveRequestsForVehicle(DeliveryPlan plan, Vehicle vehicle) {
    plan.requests().forEach(request -> {
      request.assignVehicle(vehicle.getId());
      request.annotateDecision(
          plan.decisionReason(),
          plan.routeSummary(),
          plan.cargoSummary(),
          plan.comparisonSummary(),
          plan.planBenefit(),
          plan.netScore());
    });
  }

  private void reserveDecisionDetails(DeliveryPlan plan) {
    plan.requests().forEach(request -> request.annotateDecision(
        plan.decisionReason(),
        plan.routeSummary(),
        plan.cargoSummary(),
        plan.comparisonSummary(),
        plan.planBenefit(),
        plan.netScore()));
  }

  /**
   * Called from coordinatedPlanningRound() while already holding approvalLock.
   * This version does NOT acquire the lock (caller responsibility).
   */
  private DeliveryPlan refreshApprovalPendingPlanUnsafe(Vehicle vehicle, Depot mainDepot, DeliveryPlan existingPlan) {
    DeliveryPlan refreshedPlan = refreshPlanForDispatch(vehicle, mainDepot, existingPlan);
    approvalPendingPlansByVehicle.put(vehicle.getId(), refreshedPlan);
    reserveDecisionDetails(refreshedPlan);
    return refreshedPlan;
  }

  private DeliveryPlan refreshPlanForDispatch(Vehicle vehicle, Depot mainDepot, DeliveryPlan existingPlan) {
    if (existingPlan == null || mainDepot == null || existingPlan.requests().isEmpty()) {
      return existingPlan;
    }
    DeliveryPlan refreshedPlan = buildPlan(vehicle, mainDepot, existingPlan.requests());
    if (refreshedPlan == null) {
      return existingPlan;
    }
    return refreshedPlan.withDecision(existingPlan.decisionReason(), existingPlan.comparisonSummary());
  }

  private double requestDelayPenalty(ReplenishmentRequest request) {
    long age = Math.max(0L, currentTick - request.getCreatedTick());
    long downstreamMaxDelay = pendingOrders.stream()
        .filter(order -> request.getTargetDepotId().equals(order.getMicroDepotNodeId()))
        .mapToLong(this::deliveryTicks)
        .max()
        .orElse(0L);
    long effectiveDelay = Math.max(age, downstreamMaxDelay);
    return effectiveDelay * effectiveDelay;
  }

  private double stockoutPenaltyForDepot(String depotId) {
    double penalty = 0.0;
    Set<String> products = new HashSet<>(inventoryService.getDepotStock(depotId).keySet());
    pendingOrders.stream()
        .filter(order -> depotId.equals(order.getMicroDepotNodeId()))
        .flatMap(order -> order.getItems().stream())
        .map(OrderLine::getProductId)
        .forEach(products::add);
    for (String productId : products) {
      int available = inventoryService.getAvailableStock(depotId, productId);
      int pendingDemand = pendingDemandForProduct(depotId, productId);
      int baseline = inventoryService.getBaselineStock(depotId, productId);
      int threshold = (int) Math.floor(baseline * reorderPointFraction);
      if (available <= 0) {
        penalty += 25.0 + pendingDemand * 4.0;
      } else if (available < threshold) {
        penalty += (threshold - available) * 1.5;
      }
    }
    return penalty;
  }

  private double vehicleOpportunityCost(Vehicle vehicle, long missionTicks) {
    long idle = vehicleIdleTicks.getOrDefault(vehicle.getId(), 0L);
    long working = vehicleWorkingTicks.getOrDefault(vehicle.getId(), 0L);
    double utilization = (double) working / Math.max(1L, idle + working);
    double underUtilizationPenalty = Math.abs(utilization - 0.65) * 12.0;
    return missionTicks * (1.0 + utilization) + underUtilizationPenalty;
  }

  private boolean isUrgentRequest(ReplenishmentRequest request) {
    if (currentTick - request.getCreatedTick() >= URGENT_ORDER_DELAY_TICKS) {
      return true;
    }
    return pendingOrders.stream()
        .filter(order -> request.getTargetDepotId().equals(order.getMicroDepotNodeId()))
        .anyMatch(order -> order.isHighPriority() || deliveryTicks(order) >= URGENT_ORDER_DELAY_TICKS);
  }

  private void handleVehicleCompletions() {
    List<VehicleMovementService.TickResult> results = vehicleMovementService.tick(vehicles, currentTick, graph);
    for (VehicleMovementService.TickResult result : results) {
      Vehicle vehicle = vehicles.stream().filter(item -> item.getId().equals(result.vehicleId())).findFirst()
          .orElse(null);
      if (vehicle != null) {
        vehicleMovementService.getCurrentNodeId(vehicle.getId()).ifPresent(vehicle::setLocationNodeId);
      }
      for (VehicleMovementService.CompletedStop stop : result.completedStops()) {
        if (vehicle != null) {
          vehicle.removeLoad(Math.min(vehicle.getCurrentLoad(), stop.volume()));
        }
        for (String requestId : stop.requestIds()) {
          ReplenishmentRequest request = activeReplenishments.get(requestId);
          if (request == null) {
            continue;
          }
          inventoryService.addItems(stop.depotId(), request.getItems());
          recordDepotStockChange(stop.depotId(), request.getItems(), 1);
          request.complete();
          activeReplenishments.remove(request.getId());
          replenishmentPlanDirty = true;
          fulfillOrdersForDepot(request.getTargetDepotId(), vehicle != null ? vehicle.getId() : "UNKNOWN");
        }
      }

      VehicleMovementService.CompletedAssignment completion = result.completedAssignment();
      if (completion == null || vehicle == null) {
        continue;
      }
      // Thesis Analytics: Split routeCost into Fuel and Tolls
      double fuelCost = completion.distanceKm() * vehicle.getCostPerKm();
      double tollCost = Math.max(0, completion.routeCost() - fuelCost);

      vehicleReportService.recordTrip(
          vehicle.getId(),
          vehicle.getFuelType(),
          completion.startedAtTick(),
          completion.completedAtTick(),
          completion.distanceKm(),
          completion.cost(),
          completion.volume(),
          completion.routeType(),
          completion.loadFraction(),
          completion.waitTicks(),
          completion.loadingTicks(),
          completion.unloadingTicks(),
          completion.stopCount(),
          completion.planBenefit(),
          completion.netScore(),
          fuelCost,
          tollCost,
          completion.handlingCost(),
          completion.timeCost(),
          completion.delayPenalty(),
          completion.stockoutPenalty(),
          vehicle.getCurrentMissionCargoValue());

      if ("POSITIONING".equals(completion.routeType())) {
        DeliveryPlan stagedPlan = stagedPlansByVehicle.get(vehicle.getId());
        if (stagedPlan != null) {
          double loadFraction = vehicle.getCapacityVolume() <= 0 ? 0.0
              : stagedPlan.totalVolume() / vehicle.getCapacityVolume();
          if (requireManualDispatchApproval && loadFraction < 0.75) {
            vehicle.setStatus(VehicleStatus.IDLE);
            vehicle.setWaitingSinceTick(currentTick);
            replenishmentPlanDirty = true;
            queuePlanForApproval(vehicle, stagedPlan, 0L);
          } else {
            if (!dispatchPlan(vehicle, stagedPlan, 0L)) {
              vehiclesCompletedThisTick.add(vehicle.getId());
            } else if (requireManualDispatchApproval) {
              eventLogService.record(currentTick, "VEHICLE_AUTO_DISPATCH", "Otomatik dispatch (Rutin Operasyon)",
                  String.format(java.util.Locale.US,
                      "[%s] doluluk %.0f%% (>= 75%%) oldugu icin onay beklemeden yola cikarildi.", vehicle.getId(),
                      loadFraction * 100.0),
                  "VEHICLE", vehicle.getId());
            }
          }
          continue;
        }
      }

      if ("REPLENISHMENT".equals(completion.routeType())) {
        assignReturnToMain(vehicle);
      } else {
        vehicle.setStatus(VehicleStatus.IDLE);
        vehicle.setWaitingSinceTick(currentTick);
        replenishmentPlanDirty = true;
        vehiclesCompletedThisTick.add(vehicle.getId());
      }
    }
  }

  private void fulfillOrdersForDepot(String depotId, String vehicleId) {
    Iterator<Order> iterator = pendingOrders.iterator();
    while (iterator.hasNext()) {
      Order order = iterator.next();
      if (!depotId.equals(order.getMicroDepotNodeId())) {
        continue;
      }
      List<OrderLine> newlyReserved = inventoryService.reserveAvailable(depotId, order.getUnreservedItems());
      order.reserveItems(newlyReserved);
      if (!order.isFullyReserved()) {
        continue;
      }
      List<OrderLine> reservedItems = order.getReservedItems();
      inventoryService.consumeReserved(depotId, reservedItems);
      recordDepotStockChange(depotId, reservedItems, -1);
      order.clearReservedItems();
      if (order.getStatus() == OrderStatus.WAITING_REPLENISHMENT) {
        order.fulfillFromMicroStock();
      }
      order.complete();
      eventBus.publish(new com.tezprojesi.events.DeliveryCompletedEvent(order.getId(), vehicleId,
          orderCreatedTick.getOrDefault(order.getId(), currentTick), currentTick));
      eventLogService.record(currentTick, "ORDER_FULFILLED", "Siparis replenishment sonrasi karsilandi",
          order.getId() + " siparisi " + depotId + " deposuna gelen ikmal sonrasi karsilandi.", "ORDER", order.getId());
      iterator.remove();
      triggerDemandDrivenReplenishment(depotId);
    }
  }

  private String depotNode(String depotId) {
    return depots.stream()
        .filter(depot -> depot.getId().equals(depotId))
        .findFirst()
        .map(Depot::getNodeId)
        .orElseThrow();
  }

  private Depot mainDepot() {
    return depots.stream().filter(Depot::isMain).findFirst().orElse(null);
  }

  private void assignReturnToMain(Vehicle vehicle) {
    Depot mainDepot = depots.stream().filter(depot -> depot.getType() == DepotType.MAIN).findFirst().orElse(null);
    if (mainDepot == null || mainDepot.getNodeId().equals(vehicle.getLocationNodeId())) {
      vehicle.setStatus(VehicleStatus.IDLE);
      vehicle.setWaitingSinceTick(currentTick);
      replenishmentPlanDirty = true;
      vehiclesCompletedThisTick.add(vehicle.getId());
      return;
    }
    List<String> route = computeVehicleRoute(vehicle, vehicle.getLocationNodeId(), mainDepot.getNodeId());
    if (route.isEmpty()) {
      vehicle.setStatus(VehicleStatus.IDLE);
      vehicle.setWaitingSinceTick(currentTick);
      replenishmentPlanDirty = true;
      vehiclesCompletedThisTick.add(vehicle.getId());
      return;
    }
    double distance = computeDistance(route);
    
    // MALIYET HESABI: YAKIT + GISE (TOLL)
    double fuelCost = distance * vehicle.getCostPerKm();
    double totalTollCost = 0.0;
    for (int i = 0; i < route.size() - 1; i++) {
        Edge e = graph.getEdge(route.get(i), route.get(i+1));
        if (e != null) totalTollCost += e.getTollCost();
    }
    if (vehicle.getCapacityVolume() >= HEAVY_VEHICLE_CAPACITY_THRESHOLD) {
        totalTollCost *= HEAVY_VEHICLE_TOLL_MULTIPLIER;
    }
    double legCost = fuelCost + totalTollCost;
    long returnTicks = computeTravelTicks(route, vehicle);
    long idealArrivalTick = currentTick + returnTicks;
    
    // Donus fazinda da gidis asamasindaki toplam ROI ve maliyeti hatirlatiyoruz
    String comparisonSummary = String.format(java.util.Locale.US,
        "Tam Misyon Maliyeti: %.1f TL | Misyon ROI: %.1fx (Geri Donus Fazinda)",
        vehicle.getCurrentMissionTotalCost(), vehicle.getCurrentMissionBcrRatio());

    String routeSummary = String.format(java.util.Locale.US,
        "%s -> Main depo. (Mesafe: %.1f km | Tahmini Varis: %s)",
        vehicle.getLocationNodeId(), distance, formatTickToTime(idealArrivalTick));

    vehicle.setStatus(VehicleStatus.RETURNING);
    vehicle.setWaitingSinceTick(-1L);
    metricsEventHandler.recordDistance(vehicle.getId(), distance);
    
    vehicleMovementService.assignRoute(
        vehicle.getId(),
        "return-" + vehicle.getId() + "-" + currentTick,
        route,
        buildSegmentPlans(route, vehicle),
        List.of(),
        currentTick,
        0.0,
        "RETURN",
        distance,
        legCost,
        vehicle.getCapacityVolume(),
        0.0,
        0L,
        0L,
        "Arac gorev sonrasi ana depoya geri donuyor.",
        "Bos donus rotasi baslatildi.",
        vehicle.getLocationNodeId() + " -> Main depo",
        "Bos arac (Geri Donus)",
        comparisonSummary,
        legCost,
        0.0, // planBenefit
        -legCost, // netScore
        computeTravelTicks(route, vehicle),
        distance,
        0.0,
        legCost,
        0.0,
        0.0,
        0.0,
        0.0,
        0.0,
        0.0);
  }

  private List<OrderLine> mergeItems(List<OrderLine> items) {
    Map<String, OrderLine> merged = new LinkedHashMap<>();
    for (OrderLine item : items) {
      OrderLine current = merged.get(item.getProductId());
      if (current == null) {
        merged.put(item.getProductId(), item);
      } else {
        merged.put(item.getProductId(), new OrderLine(
            item.getProductId(),
            current.getQuantity() + item.getQuantity(),
            current.getUnitVolume()));
      }
    }
    return List.copyOf(merged.values());
  }

  private List<MergedStop> mergeRequestsByDepot(List<ReplenishmentRequest> requests, Vehicle vehicle) {
    Map<String, List<ReplenishmentRequest>> groupedByDepot = new LinkedHashMap<>();
    for (ReplenishmentRequest request : requests) {
      groupedByDepot.computeIfAbsent(request.getTargetDepotId(), ignored -> new ArrayList<>()).add(request);
    }

    List<MergedStop> mergedStops = new ArrayList<>();
    for (List<ReplenishmentRequest> depotRequests : groupedByDepot.values()) {
      List<OrderLine> depotItems = mergeItems(depotRequests.stream()
          .flatMap(request -> request.getItems().stream())
          .toList());
      double depotVolume = depotItems.stream().mapToDouble(OrderLine::getLineVolume).sum();
      if (depotVolume <= vehicle.getCapacityVolume()) {
        mergedStops.add(new MergedStop(
            depotRequests.get(0).getTargetDepotId(),
            depotRequests.stream().map(ReplenishmentRequest::getId).toList(),
            depotItems,
            depotVolume));
        continue;
      }
      for (ReplenishmentRequest request : depotRequests) {
        mergedStops.add(new MergedStop(
            request.getTargetDepotId(),
            List.of(request.getId()),
            request.getItems(),
            request.getTotalVolume()));
      }
    }
    return List.copyOf(mergedStops);
  }

  private double immediateDepartureLoadFraction(Vehicle vehicle) {
    return 0.75;
  }

  private double computeDistance(List<String> route) {
    double total = 0.0;
    for (int i = 0; i < route.size() - 1; i++) {
      var edge = graph.getEdge(route.get(i), route.get(i + 1));
      if (edge != null) {
        total += edge.getDistanceKm();
      }
    }
    return total;
  }

  private double computeDistanceBetween(String fromNode, String toNode) {
    List<String> route = routingService.computeShortestPath(graph, fromNode, toNode);
    if (route.isEmpty()) {
      return Double.POSITIVE_INFINITY;
    }
    return computeDistance(route);
  }

  private double computeDistanceBetween(Vehicle vehicle, String fromNode, String toNode) {
    List<String> route = computeVehicleRoute(vehicle, fromNode, toNode);
    if (route.isEmpty()) {
      return Double.POSITIVE_INFINITY;
    }
    return computeDistance(route);
  }

  private long computeTravelTicks(List<String> route, Vehicle vehicle) {
    return buildSegmentPlans(route, vehicle).stream().mapToLong(VehicleMovementService.SegmentPlan::travelTicks).sum();
  }

  private long computeTravelTicksBetween(String fromNode, String toNode, Vehicle vehicle) {
    List<String> route = computeVehicleRoute(vehicle, fromNode, toNode);
    return route.isEmpty() ? Long.MAX_VALUE / 4 : computeTravelTicks(route, vehicle);
  }

  private String formatTickToTime(long tick) {
    // Simülasyon sabah 08:00'de başlar
    long totalMinutes = 8 * 60 + tick;
    long hours = (totalMinutes / 60) % 24;
    long minutes = totalMinutes % 60;
    return String.format("%02d:%02d", hours, minutes);
  }

  private double computeTotalCargoFinancialValue(List<OrderLine> items) {
    if (items == null)
      return 0.0;
    return items.stream()
        .mapToDouble(line -> productCatalogService.findById(line.getProductId())
            .map(p -> p.getFinancialValue() * line.getQuantity())
            .orElse(0.0))
        .sum();
  }

  private List<VehicleMovementService.SegmentPlan> buildSegmentPlans(List<String> route, Vehicle vehicle) {
    List<VehicleMovementService.SegmentPlan> segments = new ArrayList<>();
    for (int index = 0; index < route.size() - 1; index++) {
      String fromNodeId = route.get(index);
      String toNodeId = route.get(index + 1);
      var edge = graph.getEdge(fromNodeId, toNodeId);
      if (edge == null) {
        throw new IllegalStateException("Route contains a missing edge: " + fromNodeId + " -> " + toNodeId);
      }
      double tickDurationHours = TICK_MINUTES / 60.0;
      double effectiveSpeedKmPerHour = Math.max(0.1,
          vehicle.getAverageSpeedKmPerHour() / Math.max(0.1, edge.getTrafficFactor()));
      long travelTicks = Math.max(1L,
          (long) Math.ceil((edge.getDistanceKm() / effectiveSpeedKmPerHour) / tickDurationHours));
      segments.add(new VehicleMovementService.SegmentPlan(fromNodeId, toNodeId, edge.getDistanceKm(), travelTicks));
    }
    return segments;
  }

  private double computeRouteCost(List<String> route, Vehicle vehicle) {
    return routingService.computePathCost(graph, route, routingProfileFor(vehicle));
  }

  private long computeLoadTicks(Vehicle vehicle, double volume) {
    // RULE: Per user request for thesis demo, minimum loading time is set to 60
    // ticks (1 hour).
    long baseTicks = 60L;
    return baseTicks + (long) Math.ceil(volume / vehicle.getLoadingRateVolumePerTick());
  }

  private long computeUnloadTicks(Vehicle vehicle, double volume) {
    // RULE: Per user request for thesis demo, minimum unloading time is set to 60
    // ticks (1 hour).
    long baseTicks = 60L;
    return baseTicks + (long) Math.ceil(volume / vehicle.getUnloadingRateVolumePerTick());
  }

  private void autoRefillMainDepot() {
    Depot mainDepot = mainDepot();
    if (mainDepot == null) {
      return;
    }
    Set<String> products = new HashSet<>(inventoryService.getDepotStock(mainDepot.getId()).keySet());
    replenishmentService.findOpenRequests().stream()
        .flatMap(request -> request.getItems().stream())
        .map(OrderLine::getProductId)
        .forEach(products::add);

    for (String productId : products) {
      int reserved = inventoryService.getReservedStock(mainDepot.getId(), productId);
      int desired = Math.max(
          inventoryService.getBaselineStock(mainDepot.getId(), productId),
          reserved + replenishmentService.findOpenRequests().stream()
              .flatMap(request -> request.getItems().stream())
              .filter(item -> item.getProductId().equals(productId))
              .mapToInt(OrderLine::getQuantity)
              .sum());
      int current = inventoryService.getTotalStock(mainDepot.getId(), productId);
      if (current < desired) {
        int restockQuantity = desired - current;
        inventoryService.addStock(mainDepot.getId(), productId, restockQuantity);
        metricsEventHandler.recordDepotStockChange(mainDepot.getId(), restockQuantity);
      }
    }
  }

  public void submitOrder(Order order) {
    synchronized (lifecycleMonitor) {
      if (order.getMicroDepotNodeId() == null) {
        orderGenerator.assignNearestMicroDepot(order, depots, graph);
      }
      if (order.getCreationTick() == 0L) {
        order.setCreationTick(currentTick);
      }
      pendingOrders.add(order);
      activeOrders.put(order.getId(), order);
      orderRepository.save(order);
      orderCreatedTick.put(order.getId(), order.getCreationTick());
      metricsEventHandler.recordOrderCreated(order.getId());
      eventBus.publish(new OrderCreatedEvent(order.getId(), order.getCustomerNodeId(), order.getDemandVolume()));
      eventLogService.record(currentTick, "ORDER_CREATED", "Siparis olustu", order.getId() + " siparisi "
          + order.getCustomerNodeId() + " musterisi icin olusturuldu. Mikro depo: " + order.getMicroDepotNodeId(),
          "ORDER", order.getId());
    }
  }

  public void submitReplenishmentRequest(ReplenishmentRequest request) {
    synchronized (lifecycleMonitor) {
      if (!inventoryService.hasEnoughAvailable(request.getSourceDepotId(), request.getItems())) {
        throw new IllegalStateException(
            "Source depot does not have enough available stock for replenishment " + request.getId());
      }
      boolean targetMicro = depots.stream()
          .anyMatch(d -> d.getId().equals(request.getTargetDepotId()) && d.isMicro());
      double incomingVolume = replenishmentService.getIncomingVolume(request.getTargetDepotId());
      inventoryService.validateMicroDepotInboundCapacity(
          request.getTargetDepotId(), request.getItems(), productCatalogService, targetMicro, incomingVolume);
      activeReplenishments.put(request.getId(), request);
      replenishmentPlanDirty = true;
      eventLogService.record(currentTick, "REPLENISHMENT_REQUESTED", "Manuel replenishment talebi",
          request.getTargetDepotId() + " deposu icin manuel ikmal talebi acildi.", "REPLENISHMENT", request.getId());
    }
  }

  public void startAsync() {
    synchronized (lifecycleMonitor) {
      if (running) {
        return;
      }
      running = true;
      stopRequested = false;
      lastFailureMessage = null;
      lastFailureTick = -1L;
    }
    Thread worker = new Thread(this::executeRunLoop, "simulation-engine");
    worker.setDaemon(true);
    worker.setUncaughtExceptionHandler((t, e) -> {
      if (e instanceof RuntimeException) {
        recordFailure((RuntimeException) e);
      }
    });
    worker.start();
  }

  public void stop() {
    stopRequested = true;
  }

  public void submitManualReplenishmentRequest(String targetDepotId, String productId, int quantity) {
    if (quantity <= 0)
      return;
    Product product = productCatalogService.findById(productId).orElse(null);
    if (product == null)
      return;

    // KONTROL: Depo kapasitesi (100) aşılıyor mu?
    int currentStock = inventoryService.getTotalStock(targetDepotId, productId);
    // getIncomingQuantity zaten hem CREATED hem IN_TRANSIT olanları sayıyor
    int incoming = replenishmentService.getIncomingQuantity(targetDepotId, productId);

    if (currentStock + incoming + quantity > DispatchConstants.MAX_PRODUCT_CAPACITY) {
      throw new BadRequestException(
          String.format(
              "Kapasite asimi! %s deposu icin %s urunu kapasitesi (%d) asiliyor. Mevcut: %d, Yolda: %d, Istenen: %d",
              targetDepotId, productId, DispatchConstants.MAX_PRODUCT_CAPACITY, currentStock, incoming, quantity));
    }

    List<OrderLine> items = List.of(new OrderLine(productId, quantity, product.getUnitVolume()));
    // Manuel talepler her zaman MAIN depodan mikro depolara doğru olur
    Depot sourceDepot = mainDepot();
    if (sourceDepot == null) {
      eventLogService.record(currentTick, "ERROR", "Ana depo bulunamadi",
          "Manuel ikmal icin kaynak (MAIN) depo belirlenemedi.", "SYSTEM", "engine");
      return;
    }
    ReplenishmentRequest request = replenishmentService.create(sourceDepot.getId(), targetDepotId, items, currentTick);
    request.setManual(true);
    activeReplenishments.put(request.getId(), request);
    replenishmentPlanDirty = true;

    eventLogService.record(
        currentTick,
        "MANUAL_REPLENISHMENT_SUBMITTED",
        "Manuel Gorev Olusturuldu",
        String.format("%s deposu için %d adet %s talebi sisteme eklendi. Kare arac gorevlendirilecek.",
            targetDepotId, quantity, productId),
        "DEPOT",
        targetDepotId);
  }

  public boolean isRunning() {
    return running;
  }

  public long getCurrentTick() {
    return currentTick;
  }

  public int getMaxTicks() {
    return maxTicks;
  }

  public void recordFailure(RuntimeException exception) {
    lastFailureMessage = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    lastFailureTick = currentTick;
    eventLogService.record(
        currentTick,
        "SIMULATION_ERROR",
        "Simulasyon hatasi olustu",
        "Simulasyon " + currentTick + ". dakika civarinda durdu: " + lastFailureMessage,
        "SIMULATION",
        "engine");
  }

  public boolean hasFailure() {
    return lastFailureMessage != null && !lastFailureMessage.isBlank();
  }

  public String getLastFailureMessage() {
    return lastFailureMessage;
  }

  public long getLastFailureTick() {
    return lastFailureTick;
  }

  public void reset() {
    synchronized (lifecycleMonitor) {
      if (running) {
        throw new IllegalStateException("Cannot reset while running");
      }
      activeOrders.clear();
      pendingOrders.clear();
      activeReplenishments.clear();
      stagedPlansByVehicle.clear();
      approvalPendingPlansByVehicle.clear();
      approvalWaitingSinceTick.clear();
      approvalTimeoutLimitTicks.clear();
      dispatchReadinessByVehicle.clear();
      plannedRequestIds.clear();
      replenishmentPlanDirty = true;
      orderCreatedTick.clear();
      orderRepository.clear();
      vehicleRepository.clear();
      replenishmentService.clear();
      eventLogService.clear();
      metricsEventHandler.reset();
      vehicleReportService.clear();
      vehicleMovementService.reset();
      vehicleIdleTicks.clear();
      vehicleWorkingTicks.clear();
      lastDemandDrivenReplenishmentTickByProduct.clear();
      vehiclesCompletedThisTick.clear();
      utilityRecordedThisTick.clear();
      capacityBannerEmittedForVehicle.clear();
      restoreInitialState();
      stopRequested = false;
      currentTick = 0;
      lastFailureMessage = null;
      lastFailureTick = -1L;
    }
  }

  /**
   * Güvenli restart işlemi: Worker thread'in durmasını bekle, state'i sıfırla,
   * yeniden başlat.
   * Bu metod concurrent başlangıç sorunlarını önler.
   */
  public void resetAndRestart() {
    synchronized (lifecycleMonitor) {
      if (running) {
        stopRequested = true;
        // Worker thread'in durdurmasını bekle (max 2 saniye)
        try {
          long waitStart = System.currentTimeMillis();
          while (running && System.currentTimeMillis() - waitStart < 2000) {
            lifecycleMonitor.wait(100);
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
    }
    // Lock serbest bırakıldı, şimdi reset çağır
    reset();
    // Ardından başlat
    startAsync();
  }

  public void registerVehicle(Vehicle vehicle) {
    Objects.requireNonNull(vehicle, "vehicle must not be null");
    if (vehicles.stream().anyMatch(v -> v.getId().equals(vehicle.getId()))) {
      throw new IllegalArgumentException("Vehicle already exists: " + vehicle.getId());
    }
    vehicles.add(vehicle);
    vehicleRepository.save(vehicle);
    replenishmentPlanDirty = true;
    if (initialStateCaptured) {
      initialVehicles = new java.util.ArrayList<>(initialVehicles);
      initialVehicles.add(copyVehicle(vehicle));
    }
  }

  public List<Depot> getDepots() {
    return depots;
  }

  public List<Vehicle> getVehicles() {
    return List.copyOf(vehicles);
  }

  public Map<String, Order> getActiveOrders() {
    return Map.copyOf(activeOrders);
  }

  public int getPendingOrderCount() {
    return pendingOrders.size();
  }

  public Map<String, Double> getDepotStock() {
    Map<String, Double> computed = new HashMap<>();
    inventoryService.snapshot().forEach((depotId, items) -> {
      double totalVolume = items.entrySet().stream()
          .mapToDouble(entry -> productCatalogService.findById(entry.getKey())
              .map(product -> product.getUnitVolume() * entry.getValue()).orElse(0.0))
          .sum();
      computed.put(depotId, totalVolume);
    });
    return Map.copyOf(computed);
  }

  public MetricsEventHandler getMetrics() {
    return metricsEventHandler;
  }

  public List<ReplenishmentRequest> getReplenishments() {
    return replenishmentService.findAll();
  }

  public List<VehicleMovementService.AssignmentSnapshot> getVehicleAssignments() {
    return vehicleMovementService.getActiveAssignments();
  }

  public List<DispatchReadinessSnapshot> getDispatchReadiness() {
    return dispatchReadinessByVehicle.values().stream()
        .sorted(Comparator.comparing(DispatchReadinessSnapshot::awaitingApproval).reversed()
            .thenComparingLong(DispatchReadinessSnapshot::firstRequestTick))
        .toList();
  }

  public void approveReadyDispatch(String vehicleId) {
    if (!requireManualDispatchApproval) {
      throw new IllegalStateException("Manuel dispatch onayi devre disi. approveDispatch() kullaniniz.");
    }
    approveDispatch(vehicleId);
  }

  /**
   * Human-in-the-loop approval entry point — works regardless of
   * requireManualDispatchApproval.
   * Any plan flagged WAITING_FOR_APPROVAL (either via
   * requireManualDispatchApproval flag or
   * critical cost / route deviation threshold) can be approved via this method.
   *
   * Thread-safety: protected by approvalLock so that concurrent REST calls and
   * the
   * simulation-engine thread cannot corrupt approvalPendingPlansByVehicle
   * simultaneously.
   */
  public void approveDispatch(String vehicleId) {
    approvalLock.lock();
    try {
      DeliveryPlan plan = approvalPendingPlansByVehicle.get(vehicleId);
      if (plan == null) {
        throw new IllegalStateException("Vehicle " + vehicleId + " icin onay bekleyen bir gorev bulunamadi.");
      }
      Vehicle vehicle = vehicles.stream()
          .filter(item -> item.getId().equals(vehicleId))
          .findFirst()
          .orElseThrow(() -> new IllegalStateException("Vehicle " + vehicleId + " bulunamadi."));
      if (!vehicle.getLocationNodeId().equals(mainDepot() == null ? "" : mainDepot().getNodeId())) {
        throw new IllegalStateException("Arac ana depoda olmadigi icin dispatch baslatilamadi.");
      }
      if (vehicleMovementService.hasActiveRoute(vehicleId)) {
        throw new IllegalStateException("Aracin halihazirda aktif bir rotasi var.");
      }
      eventLogService.record(
          currentTick,
          "VEHICLE_DISPATCH_APPROVED",
          "Kullanici onayi alindi",
          String.format(java.util.Locale.US,
              "[%s] icin dispatch onayi alindi. operasyonelMaliyet=%.2f, netScore=%.2f. Rota baslatiliyor.",
              vehicleId, plan.operationalCost(), plan.netScore()),
          "VEHICLE",
          vehicleId);
      // Remove from approval queue before dispatching to prevent re-entry.
      approvalPendingPlansByVehicle.remove(vehicleId);
      approvalWaitingSinceTick.remove(vehicleId);
      approvalTimeoutLimitTicks.remove(vehicleId);
      long waitedTicks = computeWaitedTicks(vehicle, mainDepot());
      dispatchPlan(vehicle, plan, waitedTicks);
    } finally {
      approvalLock.unlock();
    }
  }

  /**
   * Enforces the per-vehicle 45–60 tick approval window: auto-dispatch if load ≥
   * 50%, else cancel plan.
   */
  private void checkApprovalTimeouts() {
    approvalLock.lock();
    try {
      List<String> timedOut = approvalWaitingSinceTick.entrySet().stream()
          .filter(entry -> {
            long limit = approvalTimeoutLimitTicks.getOrDefault(entry.getKey(), 52L);
            return currentTick - entry.getValue() >= limit;
          })
          .map(Map.Entry::getKey)
          .toList();
      for (String vehicleId : timedOut) {
        DeliveryPlan expiredPlan = approvalPendingPlansByVehicle.remove(vehicleId);
        approvalWaitingSinceTick.remove(vehicleId);
        Long waitedBox = approvalTimeoutLimitTicks.remove(vehicleId);
        long waited = waitedBox != null ? waitedBox : 52L;
        dispatchReadinessByVehicle.remove(vehicleId);
        Vehicle vehicle = vehicles.stream().filter(v -> v.getId().equals(vehicleId)).findFirst().orElse(null);
        if (expiredPlan != null && vehicle != null) {
          double loadFraction = vehicle.getCapacityVolume() <= 0 ? 0.0
              : expiredPlan.totalVolume() / vehicle.getCapacityVolume();
          if (loadFraction >= 0.50) {
            dispatchPlan(vehicle, expiredPlan, waited);
            eventLogService.record(
                currentTick,
                "VEHICLE_AUTO_DISPATCH",
                "Zaman asimi sonrasi oto-cikis",
                String.format(java.util.Locale.US,
                    "[%s] araci %d tick onay bekledi; doluluk %.0f%% (>= 50%%) — oto-cikis basladi.",
                    vehicleId, waited, loadFraction * 100.0),
                "VEHICLE",
                vehicleId);
          } else {
            releasePlannedRequests(expiredPlan);
            vehicle.setStatus(VehicleStatus.IDLE);
            replenishmentPlanDirty = true;
            eventLogService.record(
                currentTick,
                "VEHICLE_APPROVAL_TIMEOUT",
                "Onay suresi doldu",
                String.format(java.util.Locale.US,
                    "[%s] araci onay penceresi doldu. Doluluk (%.0f%%) 50%% altinda; plan iptal.",
                    vehicleId, loadFraction * 100.0),
                "VEHICLE",
                vehicleId);
          }
        }
      }
    } finally {
      approvalLock.unlock();
    }
  }

  public void captureInitialState() {
    initialStateCaptured = true;
    initialNodes = graph.getAllNodes().stream().map(this::copyNode).toList();
    initialEdges = graph.getEdges().stream().map(this::copyEdge).toList();
    initialDepots = depots.stream().map(this::copyDepot).toList();
    initialProducts = productCatalogService.findAll().stream().map(this::copyProduct).toList();
    initialVehicles = vehicles.stream().map(this::copyVehicle).toList();
    initialInventorySnapshot = inventoryService.snapshot();
  }

  private void restoreInitialState() {
    if (initialStateCaptured) {
      graph.clear();
      initialNodes.forEach(graph::addNode);
      initialEdges.forEach(graph::addEdge);

      depots.clear();
      depots.addAll(initialDepots.stream().map(this::copyDepot).toList());

      productCatalogService.clear();
      initialProducts.stream().map(this::copyProduct).forEach(productCatalogService::save);

      vehicles.clear();
      List<Vehicle> restoredVehicles = initialVehicles.stream().map(this::copyVehicle).toList();
      vehicles.addAll(restoredVehicles);
      restoredVehicles.forEach(vehicleRepository::save);

      inventoryService.restore(initialInventorySnapshot);
      approvalPendingPlansByVehicle.clear();
      approvalWaitingSinceTick.clear();
      approvalTimeoutLimitTicks.clear();
      dispatchReadinessByVehicle.clear();
      capacityBannerEmittedForVehicle.clear();
      return;
    }

    if (!initialInventorySnapshot.isEmpty()) {
      inventoryService.restore(initialInventorySnapshot);
    }
    for (Vehicle vehicle : vehicles) {
      vehicle.resetOperationalState(vehicle.getLocationNodeId());
    }
  }

  private void recordDepotStockChange(String depotId, List<OrderLine> items, int direction) {
    int multiplier = direction >= 0 ? 1 : -1;
    long quantityChange = items.stream()
        .mapToLong(OrderLine::getQuantity)
        .sum() * multiplier;
    metricsEventHandler.recordDepotStockChange(depotId, quantityChange);
  }

  private Node copyNode(Node node) {
    return new Node(node.getId(), node.getType(), node.getX(), node.getY());
  }

  private Edge copyEdge(Edge edge) {
    return new Edge(edge.getFromNodeId(), edge.getToNodeId(), edge.getDistanceKm(), edge.getTrafficFactor(),
        edge.getTollCost());
  }

  private Depot copyDepot(Depot depot) {
    return new Depot(depot.getId(), depot.getType(), depot.getNodeId());
  }

  private Product copyProduct(Product product) {
    return new Product(
        product.getId(),
        product.getName(),
        product.getCategory(),
        product.getUnitVolume(),
        product.getUnitWeight(),
        product.getFinancialValue(),
        product.getRequiredCapabilities());
  }

  private Vehicle copyVehicle(Vehicle vehicle) {
    return new Vehicle(
        vehicle.getId(),
        vehicle.getCapacityVolume(),
        vehicle.getLocationNodeId(),
        vehicle.getFuelType(),
        vehicle.getCostPerKm(),
        vehicle.getSupportedCategories(),
        vehicle.getCapabilities(),
        vehicle.getPlateNumber(),
        vehicle.getDriverName(),
        vehicle.getMinimumDepartureLoadFraction(),
        vehicle.getMaxStopCount(),
        vehicle.getMaxWaitTicks(),
        vehicle.getAverageSpeedKmPerHour(),
        vehicle.getLoadingRateVolumePerTick(),
        vehicle.getUnloadingRateVolumePerTick(),
        vehicle.getBaseLoadTicks(),
        vehicle.getBaseUnloadTicks(),
        vehicle.isDedicatedManual());
  }

  private long deliveryTicks(Order order) {
    return deliveryTicks(order.getId());
  }

  private long deliveryTicks(String orderId) {
    Long createdAtTick = orderCreatedTick.get(orderId);
    if (createdAtTick == null) {
      return 0L;
    }
    return Math.max(0L, currentTick - createdAtTick);
  }

  public record DispatchReadinessSnapshot(
      String vehicleId,
      String referenceId,
      List<String> requestIds,
      List<String> targetDepotIds,
      double totalVolume,
      double capacityVolume,
      double loadFraction,
      double minimumDepartureLoadFraction,
      double immediateDepartureLoadFraction,
      boolean minimumLoadReached,
      long waitTicks,
      long maxWaitTicks,
      boolean maxWaitReached,
      boolean urgent,
      boolean readyToDispatch,
      boolean awaitingApproval,
      long firstRequestTick,
      long lastRequestUpdateTick,
      String routeSummary,
      String cargoSummary,
      String decisionReason,
      String comparisonSummary,
      int stopCount,
      long missionTicks,
      double deliveryDistanceKm,
      double repositionDistanceKm,
      double planCost,
      double planBenefit,
      double netScore,
      double routeCost,
      double handlingCost,
      double timeCost,
      double extraStopCost,
      double delayPenalty,
      double stockoutPenalty,
      double opportunityCost) {
  }

  private record VehiclePlanOption(
      Vehicle vehicle,
      DeliveryPlan plan,
      long waitedTicks,
      double loadFraction,
      boolean minimumLoadReached,
      boolean maxWaitReached,
      boolean readyToDispatch,
      boolean awaitingApproval,
      double selectionCost) {
  }

  private record MergedStop(
      String targetDepotId,
      List<String> requestIds,
      List<OrderLine> mergedItems,
      double totalVolume) {
  }

  private record DeliveryPlan(
      String referenceId,
      List<ReplenishmentRequest> requests,
      List<String> route,
      List<VehicleMovementService.StopPlan> stopPlans,
      List<OrderLine> items,
      double distanceKm,
      double operationalCost,
      double totalVolume,
      double score,
      long loadingTicks,
      long missionTicks,
      boolean urgent,
      double deliveryDistanceKm,
      double repositionDistanceKm,
      double planBenefit,
      double netScore,
      double routeCost,
      double handlingCost,
      double timeCost,
      double extraStopCost,
      double delayPenalty,
      double stockoutPenalty,
      double opportunityCost,
      String decisionReason,
      String comparisonSummary,
      String routeSummary,
      String cargoSummary) {
    DeliveryPlan withDecision(String nextDecisionReason, String nextComparisonSummary) {
      return new DeliveryPlan(
          referenceId,
          requests,
          route,
          stopPlans,
          items,
          distanceKm,
          operationalCost,
          totalVolume,
          score,
          loadingTicks,
          missionTicks,
          urgent,
          deliveryDistanceKm,
          repositionDistanceKm,
          planBenefit,
          netScore,
          routeCost,
          handlingCost,
          timeCost,
          extraStopCost,
          delayPenalty,
          stockoutPenalty,
          opportunityCost,
          nextDecisionReason,
          nextComparisonSummary,
          routeSummary,
          cargoSummary);
    }
  }
}
