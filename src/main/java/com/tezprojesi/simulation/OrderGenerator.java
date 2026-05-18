package com.tezprojesi.simulation;

import com.tezprojesi.domain.Depot;
import com.tezprojesi.domain.Order;
import com.tezprojesi.domain.OrderLine;
import com.tezprojesi.domain.NodeType;
import com.tezprojesi.domain.Product;
import com.tezprojesi.events.EventBus;
import com.tezprojesi.routing.RoutingService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public final class OrderGenerator {

  private final double orderArrivalRatePerTick;
  private final ProductCatalogService productCatalogService;
  private final RoutingService routingService = new RoutingService();
  private final Random random;
  private final AtomicLong sequence = new AtomicLong(0);
  public OrderGenerator(
      double orderArrivalRatePerTick,
      List<String> microDepotIds,
      ProductCatalogService productCatalogService,
      Random random,
      EventBus eventBus) {
    if (orderArrivalRatePerTick < 0) {
      throw new IllegalArgumentException("orderArrivalRatePerTick must be >= 0");
    }
    this.orderArrivalRatePerTick = orderArrivalRatePerTick;
    this.productCatalogService = Objects.requireNonNull(productCatalogService, "productCatalogService");
    this.random = Objects.requireNonNull(random, "random");
    Objects.requireNonNull(eventBus, "eventBus");
  }

  public List<Order> generateOrders(long tick, List<Depot> depots, com.tezprojesi.routing.CityGraph graph) {
    List<String> customerNodeIds = graph.getAllNodes().stream()
        .filter(node -> node.getType() == NodeType.CUSTOMER)
        .map(com.tezprojesi.domain.Node::getId)
        .toList();
    if (customerNodeIds.isEmpty() || productCatalogService.findAll().isEmpty()) {
      return List.of();
    }

    int fixed = (int) Math.floor(orderArrivalRatePerTick);
    double fractional = orderArrivalRatePerTick - fixed;
    int count = fixed + (random.nextDouble() < fractional ? 1 : 0);

    List<Product> products = productCatalogService.findAll();
    List<Order> orders = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      String orderId = "order-" + tick + "-" + sequence.incrementAndGet();
      String customerNodeId = customerNodeIds.get(random.nextInt(customerNodeIds.size()));
      List<OrderLine> items = generateDemandProfile(products);
      int priorityLevel = items.stream()
          .map(line -> productCatalogService.findById(line.getProductId()).orElse(null))
          .filter(Objects::nonNull)
          .anyMatch(product -> product.getCategory() == com.tezprojesi.domain.ProductCategory.MEDICAL)
          ? 1
          : 0;
      Order order = new Order(orderId, customerNodeId, items, tick, priorityLevel);
      assignNearestMicroDepot(order, depots, graph);
      orders.add(order);
    }
    return orders;
  }

  private List<OrderLine> generateDemandProfile(List<Product> products) {
    List<OrderLine> items = new ArrayList<>();
    Set<String> usedProductIds = new HashSet<>();
    int lineCount = rollLineCount(products.size());
    for (int lineIndex = 0; lineIndex < lineCount; lineIndex++) {
      Product product = drawUniqueProduct(products, usedProductIds);
      if (product == null) {
        break;
      }
      int quantity = quantityFor(product);
      items.add(new OrderLine(product.getId(), quantity, product.getUnitVolume()));
      usedProductIds.add(product.getId());
    }
    return List.copyOf(items);
  }

  private int rollLineCount(int productCount) {
    if (productCount <= 1) {
      return 1;
    }
    double roll = random.nextDouble();
    if (roll < 0.58) {
      return 1;
    }
    if (roll < 0.87) {
      return Math.min(2, productCount);
    }
    return Math.min(3, productCount);
  }

  private Product drawUniqueProduct(List<Product> products, Set<String> usedProductIds) {
    List<Product> available = products.stream()
        .filter(product -> !usedProductIds.contains(product.getId()))
        .toList();
    if (available.isEmpty()) {
      return null;
    }
    return available.get(random.nextInt(available.size()));
  }

  private int quantityFor(Product product) {
    return switch (product.getCategory()) {
      case FOOD -> 2 + random.nextInt(4);
      case CLEANING -> 1 + random.nextInt(3);
      case GENERAL -> 1 + random.nextInt(3);
      case ELECTRONICS, MEDICAL -> 1 + random.nextInt(2);
    };
  }

  public void assignNearestMicroDepot(Order order, List<Depot> depots, com.tezprojesi.routing.CityGraph graph) {
    Depot bestDepot = depots.stream()
        .filter(Depot::isMicro)
        .map(depot -> new java.util.AbstractMap.SimpleEntry<Depot, List<String>>(
            depot,
            routingService.computeShortestPath(graph, depot.getNodeId(), order.getCustomerNodeId())))
        .filter(entry -> !entry.getValue().isEmpty())
        .min(java.util.Comparator.comparingDouble(
            (Map.Entry<Depot, List<String>> entry) -> routingService.computePathCost(graph, entry.getValue())))
        .map(Map.Entry::getKey)
        .orElseThrow(() -> new IllegalStateException("No reachable micro depot for customer " + order.getCustomerNodeId()));
    order.assignMicroDepot(bestDepot.getId());
  }
}
