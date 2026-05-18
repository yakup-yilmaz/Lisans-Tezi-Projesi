package com.tezprojesi.dispatch;

import com.tezprojesi.domain.Depot;
import com.tezprojesi.domain.Order;
import com.tezprojesi.routing.CityGraph;
import com.tezprojesi.routing.RoutingService;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class DepotSelector {

  public Depot selectBestMicroDepot(
      List<Depot> depots,
      Order order,
      Map<String, Double> depotStockVolume,
      CityGraph graph,
      RoutingService routingService) {

    Objects.requireNonNull(depots, "depots must not be null");
    Objects.requireNonNull(order, "order must not be null");
    Objects.requireNonNull(depotStockVolume, "depotStockVolume must not be null");
    Objects.requireNonNull(graph, "graph must not be null");
    Objects.requireNonNull(routingService, "routingService must not be null");

    return depots.stream()
        .filter(Depot::isMicro)
        .filter(depot -> depotStockVolume.getOrDefault(depot.getId(), 0.0) >= order.getDemandVolume())
        .map(depot -> new DepotScore(depot, routingService, graph, order))
        .filter(DepotScore::isReachable)
        .min(Comparator.comparingDouble(DepotScore::getCostToCustomer))
        .orElseThrow(() -> new IllegalStateException("No available micro depot with sufficient stock and route"))
        .getDepot();
  }

  private static final class DepotScore {

    private final Depot depot;
    private final double costToCustomer;
    private final boolean reachable;

    DepotScore(Depot depot, RoutingService routingService, CityGraph graph, Order order) {
      this.depot = depot;
      String destinationNodeId = order.getMicroDepotNodeId() != null ? order.getMicroDepotNodeId() : order.getCustomerNodeId();
      List<String> route = routingService.computeShortestPath(graph, depot.getNodeId(), destinationNodeId);
      if (route.isEmpty()) {
        this.costToCustomer = Double.POSITIVE_INFINITY;
        this.reachable = false;
      } else {
        this.costToCustomer = routingService.computePathCost(graph, route);
        this.reachable = true;
      }
    }

    public Depot getDepot() {
      return depot;
    }

    public double getCostToCustomer() {
      return costToCustomer;
    }

    public boolean isReachable() {
      return reachable;
    }
  }
}
