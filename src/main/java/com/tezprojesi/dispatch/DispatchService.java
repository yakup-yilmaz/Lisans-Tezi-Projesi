package com.tezprojesi.dispatch;

import com.tezprojesi.domain.Depot;
import com.tezprojesi.domain.Order;
import com.tezprojesi.domain.Vehicle;
import com.tezprojesi.events.EventBus;
import com.tezprojesi.events.OrderAssignedEvent;
import com.tezprojesi.events.VehicleAssignedEvent;
import com.tezprojesi.routing.CityGraph;
import com.tezprojesi.routing.RoutingService;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class DispatchService {

  private final DepotSelector depotSelector;
  private final VehicleSelector vehicleSelector;
  private final RoutingService routingService;
  private final EventBus eventBus;

  public DispatchService(
      DepotSelector depotSelector,
      VehicleSelector vehicleSelector,
      RoutingService routingService,
      EventBus eventBus) {
    this.depotSelector = Objects.requireNonNull(depotSelector, "depotSelector must not be null");
    this.vehicleSelector = Objects.requireNonNull(vehicleSelector, "vehicleSelector must not be null");
    this.routingService = Objects.requireNonNull(routingService, "routingService must not be null");
    this.eventBus = Objects.requireNonNull(eventBus, "eventBus must not be null");
  }

  /**
   * Assigns an order to the best eligible micro depot and available vehicle.
   *
   * @param order incoming order (delivery node is order.getMicroDepotNodeId())
   * @param depots list of known depots
   * @param depotStockVolume map from depotId to remaining volume stock
   * @param vehicles candidate vehicles to use
   * @param graph city graph for routing
   * @return details including chosen depot/vehicle and route
   */
  public DispatchResult dispatchOrder(
      Order order,
      List<Depot> depots,
      Map<String, Double> depotStockVolume,
      List<Vehicle> vehicles,
      CityGraph graph) {
    return dispatchOrder(order, depots, depotStockVolume, vehicles, graph, null);
  }

  public DispatchResult dispatchOrder(
      Order order,
      List<Depot> depots,
      Map<String, Double> depotStockVolume,
      List<Vehicle> vehicles,
      CityGraph graph,
      DispatchPolicy policy) {

    Objects.requireNonNull(order, "order must not be null");
    Objects.requireNonNull(depots, "depots must not be null");
    Objects.requireNonNull(depotStockVolume, "depotStockVolume must not be null");
    Objects.requireNonNull(vehicles, "vehicles must not be null");
    Objects.requireNonNull(graph, "graph must not be null");

    Depot selectedDepot = depotSelector.selectBestMicroDepot(depots, order, depotStockVolume, graph, routingService);

    Vehicle selectedVehicle = vehicleSelector.selectBestVehicle(vehicles, order, selectedDepot, graph, routingService);

    String destinationNodeId = order.getMicroDepotNodeId() != null
        ? order.getMicroDepotNodeId()
        : order.getCustomerNodeId();

    if (policy != null) {
      policy.checkDepartureAllowed(selectedDepot, selectedVehicle, order);
    }

    // Compute routing from depot to customer location.
    List<String> route = routingService.computeShortestPath(graph, selectedDepot.getNodeId(), destinationNodeId);
    if (route.isEmpty()) {
      throw new IllegalStateException("Selected depot cannot reach customer location");
    }

    double routeCost = routingService.computePathCost(graph, route);

    order.assignVehicle(selectedVehicle.getId());
    order.markInTransit();
    selectedVehicle.addLoad(order.getDemandVolume());
    selectedVehicle.moveTo(destinationNodeId);

    double currentStock = depotStockVolume.getOrDefault(selectedDepot.getId(), 0.0);
    if (currentStock < order.getDemandVolume()) {
      throw new IllegalStateException("Depot stock changed unexpectedly and is insufficient");
    }
    depotStockVolume.put(selectedDepot.getId(), currentStock - order.getDemandVolume());

    DispatchResult result = new DispatchResult(order.getId(), selectedDepot.getId(), selectedVehicle.getId(), route, routeCost);

    eventBus.publish(new OrderAssignedEvent(order.getId(), selectedDepot.getId(), selectedVehicle.getId(), route));
    eventBus.publish(new VehicleAssignedEvent(selectedVehicle.getId(), order.getId(), selectedDepot.getId()));

    return result;
  }
}
