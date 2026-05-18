package com.tezprojesi.dispatch;

import com.tezprojesi.domain.CapabilityMasks;
import com.tezprojesi.domain.Depot;
import com.tezprojesi.domain.Order;
import com.tezprojesi.domain.OrderLine;
import com.tezprojesi.domain.Product;
import com.tezprojesi.domain.Vehicle;
import com.tezprojesi.domain.VehicleStatus;
import com.tezprojesi.routing.CityGraph;
import com.tezprojesi.routing.RoutingService;
import com.tezprojesi.simulation.ProductCatalogService;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class VehicleSelector {

  private static final Set<VehicleStatus> AVAILABLE_STATUSES = Set.of(
      VehicleStatus.IDLE,
      VehicleStatus.LOADING,
      VehicleStatus.RETURNING);
  private static final double OPPORTUNITY_COST_WEIGHT = 0.0;

  private final ProductCatalogService productCatalogService;

  public VehicleSelector() {
    this(null);
  }

  public VehicleSelector(ProductCatalogService productCatalogService) {
    this.productCatalogService = productCatalogService;
  }

  public Vehicle selectBestVehicle(
      List<Vehicle> vehicles,
      Order order,
      Depot selectedDepot,
      CityGraph graph,
      RoutingService routingService) {

    Objects.requireNonNull(vehicles, "vehicles must not be null");
    Objects.requireNonNull(order, "order must not be null");
    Objects.requireNonNull(selectedDepot, "selectedDepot must not be null");
    Objects.requireNonNull(graph, "graph must not be null");
    Objects.requireNonNull(routingService, "routingService must not be null");

    return vehicles.stream()
        .filter(vehicle -> AVAILABLE_STATUSES.contains(vehicle.getStatus()))
        .filter(vehicle -> vehicle.getCurrentLoad() == 0.0)
        .filter(vehicle -> vehicle.canTakeLoad(order.getDemandVolume()))
        .filter(vehicle -> fastCapabilityMatch(vehicle, order))
        .map(vehicle -> new VehicleScore(vehicle, routingService, graph, selectedDepot, order))
        .filter(VehicleScore::isReachable)
        .min(Comparator.comparingDouble(VehicleScore::getTotalScore))
        .orElseThrow(() -> new IllegalStateException("No available vehicle matches capacity and proximity"))
        .getVehicle();
  }

  private boolean fastCapabilityMatch(Vehicle vehicle, Order order) {
    if (productCatalogService == null) {
      return true;
    }
    long req = 0L;
    for (OrderLine line : order.getItems()) {
      Product p = productCatalogService.findById(line.getProductId()).orElse(null);
      if (p == null) {
        return false;
      }
      req |= CapabilityMasks.productCapabilityMask(p.getRequiredCapabilities());
    }
    long vMask = CapabilityMasks.vehicleCapabilityMask(vehicle.getCapabilities());
    return CapabilityMasks.vehicleCoversRequiredMask(vMask, req);
  }

  private static final class VehicleScore {

    private final Vehicle vehicle;
    private final double costToDepot;
    private final double deliveryRouteCost;
    private final double opportunityCost;
    private final double totalScore;

    VehicleScore(Vehicle vehicle, RoutingService routingService, CityGraph graph, Depot selectedDepot, Order order) {
      this.vehicle = vehicle;
      String destinationNodeId = order.getMicroDepotNodeId() != null ? order.getMicroDepotNodeId() : order.getCustomerNodeId();
      List<String> routeToDepot = routingService.computeShortestPath(graph, vehicle.getLocationNodeId(), selectedDepot.getNodeId());
      List<String> routeToCustomer = routingService.computeShortestPath(graph, selectedDepot.getNodeId(), destinationNodeId);
      if (routeToDepot.isEmpty() || routeToCustomer.isEmpty()) {
        this.costToDepot = Double.POSITIVE_INFINITY;
        this.deliveryRouteCost = Double.POSITIVE_INFINITY;
        this.opportunityCost = Double.POSITIVE_INFINITY;
        this.totalScore = Double.POSITIVE_INFINITY;
      } else {
        this.costToDepot = routingService.computePathCost(graph, routeToDepot) * vehicle.getCostPerKm();
        this.deliveryRouteCost = routingService.computePathCost(graph, routeToCustomer) * vehicle.getCostPerKm();
        this.opportunityCost = (costToDepot + deliveryRouteCost) * OPPORTUNITY_COST_WEIGHT;
        this.totalScore = costToDepot + deliveryRouteCost + opportunityCost;
      }
    }

    public Vehicle getVehicle() {
      return vehicle;
    }

    public double getCostToDepot() {
      return costToDepot;
    }

    public boolean isReachable() {
      return Double.isFinite(totalScore);
    }

    public double getTotalScore() {
      return totalScore;
    }
  }
}
