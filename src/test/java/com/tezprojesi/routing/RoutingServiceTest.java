package com.tezprojesi.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.tezprojesi.domain.Edge;
import com.tezprojesi.domain.Node;
import com.tezprojesi.domain.NodeType;
import java.util.List;
import org.junit.jupiter.api.Test;

class RoutingServiceTest {

  @Test
  void graphCreation_addNodesAndEdges_neighborsVisible() {
    CityGraph graph = new CityGraph();
    graph.addNode(new Node("A", NodeType.INTERSECTION));
    graph.addNode(new Node("B", NodeType.INTERSECTION));

    graph.addEdge(new Edge("A", "B", 5.0, 1.2));

    List<Edge> neighbors = graph.getNeighbors("A");
    assertEquals(1, neighbors.size());
    assertEquals("B", neighbors.get(0).getToNodeId());
    assertEquals(5.0, neighbors.get(0).getDistanceKm());
    assertEquals(1.2, neighbors.get(0).getTrafficFactor());
  }

  @Test
  void computeShortestPath_picksLowerCostRoute() {
    CityGraph graph = new CityGraph();
    graph.addNode(new Node("A", NodeType.MAIN_DEPOT));
    graph.addNode(new Node("B", NodeType.MICRO_DEPOT));
    graph.addNode(new Node("C", NodeType.INTERSECTION));

    graph.addEdge(new Edge("A", "B", 2.0, 1.0)); // cost 2
    graph.addEdge(new Edge("B", "C", 2.0, 1.0)); // cost 2 => total 4
    graph.addEdge(new Edge("A", "C", 10.0, 0.5)); // cost 5

    RoutingService routing = new RoutingService();
    List<String> result = routing.computeShortestPath(graph, "A", "C");

    assertEquals(List.of("A", "B", "C"), result);
  }

  @Test
  void computeShortestPath_noRoute_returnsEmpty() {
    CityGraph graph = new CityGraph();
    graph.addNode(new Node("A", NodeType.MAIN_DEPOT));
    graph.addNode(new Node("B", NodeType.MICRO_DEPOT));
    graph.addNode(new Node("C", NodeType.INTERSECTION));

    graph.addEdge(new Edge("A", "B", 1.0, 1.0));

    RoutingService routing = new RoutingService();
    List<String> result = routing.computeShortestPath(graph, "B", "C");

    assertEquals(List.of(), result);
  }

  @Test
  void computeShortestPath_nodeMissing_throws() {
    CityGraph graph = new CityGraph();
    graph.addNode(new Node("A", NodeType.MAIN_DEPOT));

    RoutingService routing = new RoutingService();
    assertThrows(IllegalArgumentException.class, () -> routing.computeShortestPath(graph, "A", "Z"));
    assertThrows(IllegalArgumentException.class, () -> routing.computeShortestPath(graph, "X", "A"));
  }

  @Test
  void computeShortestPath_sourceEqualsTarget_singleNode() {
    CityGraph graph = new CityGraph();
    graph.addNode(new Node("A", NodeType.MAIN_DEPOT));

    RoutingService routing = new RoutingService();
    List<String> result = routing.computeShortestPath(graph, "A", "A");

    assertEquals(List.of("A"), result);
  }

  @Test
  void computeShortestPath_cachesDistinctlyPerRoutingProfile() {
    CityGraph graph = new CityGraph();
    graph.addNode(new Node("A", NodeType.MAIN_DEPOT));
    graph.addNode(new Node("B", NodeType.INTERSECTION));
    graph.addNode(new Node("C", NodeType.MICRO_DEPOT));

    // Path 1 (via B): short distance (2 km) but high toll (50 TL)
    graph.addEdge(new Edge("A", "B", 1.0, 1.0, 50.0));
    graph.addEdge(new Edge("B", "C", 1.0, 1.0, 0.0));

    // Path 2 (direct A -> C): longer distance (10 km) but zero toll (0 TL)
    graph.addEdge(new Edge("A", "C", 10.0, 1.0, 0.0));

    RoutingService routing = new RoutingService();

    // Profile 1: Light vehicle, tollWeightMultiplier = 1.0, low tollPenalty = 0.05
    // Cost via B: 2*1.0 + 50*0.05 = 4.5 TL < 10 TL => chooses A -> B -> C
    RoutingService.RoutingProfile lightProfile = new RoutingService.RoutingProfile(1.0, 0.05, 1.0, 0.12);
    List<String> pathLight = routing.computeShortestPath(graph, "A", "C", lightProfile);
    assertEquals(List.of("A", "B", "C"), pathLight);

    // Profile 2: Heavy truck, tollPenalty = 1.0, tollWeightMultiplier = 1.35
    // Cost via B: 2*1.0 + 50*1.0*1.35 = 69.5 TL > 10 TL => chooses direct A -> C
    // With cache key including routingProfile, heavy truck must NOT get cached light path!
    RoutingService.RoutingProfile heavyProfile = new RoutingService.RoutingProfile(1.0, 1.0, 1.35, 0.12);
    List<String> pathHeavy = routing.computeShortestPath(graph, "A", "C", heavyProfile);
    assertEquals(List.of("A", "C"), pathHeavy, "RoutingProfile must not be polluted by previous cached route");
  }

  @Test
  void invalidateRouteCache_forcesRecalculationOnTrafficChange() {
    CityGraph graph = new CityGraph();
    graph.addNode(new Node("A", NodeType.MAIN_DEPOT));
    graph.addNode(new Node("B", NodeType.INTERSECTION));
    graph.addNode(new Node("C", NodeType.MICRO_DEPOT));

    graph.addEdge(new Edge("A", "B", 2.0, 1.0));
    graph.addEdge(new Edge("B", "C", 2.0, 1.0));
    graph.addEdge(new Edge("A", "C", 5.0, 1.0));

    RoutingService routing = new RoutingService();
    List<String> initial = routing.computeShortestPath(graph, "A", "C");
    assertEquals(List.of("A", "B", "C"), initial);

    // Increase traffic on A -> B heavily
    graph.setEdge("A", "B", 2.0, 10.0, 0.0);
    routing.invalidateRouteCache();

    List<String> updated = routing.computeShortestPath(graph, "A", "C");
    assertEquals(List.of("A", "C"), updated, "Invalidating cache must allow Dijkstra to find new optimal route");
  }
}
