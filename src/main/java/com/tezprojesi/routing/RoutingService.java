package com.tezprojesi.routing;

import com.tezprojesi.domain.Edge;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * OPTIMIZATION: Intelligent Dijkstra Route Caching
 * 
 * Implements a LoadingCache-like mechanism to cache computed shortest paths.
 * 
 * Cache Key Format: sourceNodeId + "|" + targetNodeId + "|" + currentTrafficGeneration
 * 
 * Cache Invalidation Strategy:
 * - Each time traffic factors change significantly (tracked via trafficGeneration counter),
 *   the cache is invalidated by incrementing the generation counter.
 * - This forces Dijkstra to recalculate paths when traffic conditions change,
 *   while reusing results within the same traffic state.
 * 
 * Performance Impact:
 * - Same-tick, same-origin, same-destination queries: O(1) cache hit
 * - Different traffic state or new route: O(E log V) Dijkstra computation
 */
public final class RoutingService {

  private static final double DEFAULT_FUEL_COST_MULTIPLIER = 0.12;

  /**
   * OPTIMIZATION: Route Cache
   * 
   * Key: sourceNodeId|targetNodeId|trafficGeneration
   * Value: Shortest path (list of node IDs)
   * 
   * Implemented as ConcurrentHashMap for thread-safe access without explicit locking.
   */
  private final Map<String, CachedRoute> routeCache = new ConcurrentHashMap<>();

  /**
   * OPTIMIZATION: Traffic Generation Counter
   * 
   * Incremented whenever traffic factors change significantly.
   * Used to invalidate cache entries related to old traffic states.
   * When generation counter increments, all old cache entries become stale.
   */
  private final AtomicLong trafficGeneration = new AtomicLong(0L);

  /**
   * Maximum cache entries to prevent unbounded memory growth.
   * Implements a simple LRU-like eviction by monitoring cache size.
   */
  private static final int MAX_CACHE_SIZE = 10000;

  public List<String> computeShortestPath(CityGraph graph, String sourceNodeId, String targetNodeId) {
    return computeShortestPath(graph, sourceNodeId, targetNodeId, RoutingProfile.defaultProfile(1.0));
  }

  public List<String> computeShortestPath(CityGraph graph, String sourceNodeId, String targetNodeId, double tollPenalty) {
    return computeShortestPath(graph, sourceNodeId, targetNodeId, RoutingProfile.defaultProfile(tollPenalty));
  }

  /**
   * OPTIMIZATION: Dijkstra with Route Caching
   * 
   * Check cache first before computing. Cache hit returns O(1).
   * Cache miss triggers Dijkstra computation and stores result for future queries.
   */
  public List<String> computeShortestPath(CityGraph graph, String sourceNodeId, String targetNodeId, RoutingProfile routingProfile) {
    Objects.requireNonNull(graph, "graph must not be null");
    Objects.requireNonNull(sourceNodeId, "sourceNodeId must not be null");
    Objects.requireNonNull(targetNodeId, "targetNodeId must not be null");
    Objects.requireNonNull(routingProfile, "routingProfile must not be null");

    if (!graph.hasNode(sourceNodeId)) {
      throw new IllegalArgumentException("source node not found: " + sourceNodeId);
    }
    if (!graph.hasNode(targetNodeId)) {
      throw new IllegalArgumentException("target node not found: " + targetNodeId);
    }
    if (sourceNodeId.equals(targetNodeId)) {
      return Collections.singletonList(sourceNodeId);
    }

    // OPTIMIZATION: Cache key includes traffic generation to handle dynamic traffic
    String cacheKey = buildCacheKey(sourceNodeId, targetNodeId, trafficGeneration.get());
    
    // Check cache first (O(1) lookup)
    CachedRoute cached = routeCache.get(cacheKey);
    if (cached != null) {
      // Cache hit: return cached path
      return cached.path;
    }

    // Cache miss: compute shortest path using Dijkstra
    List<String> path = computeShortestPathDijkstra(graph, sourceNodeId, targetNodeId, routingProfile);

    // Store result in cache for future queries (if not empty)
    if (!path.isEmpty()) {
      // Evict old entries if cache is getting too large
      if (routeCache.size() >= MAX_CACHE_SIZE) {
        routeCache.clear(); // Simple eviction: clear entire cache
      }
      routeCache.put(cacheKey, new CachedRoute(path, System.nanoTime()));
    }

    return path;
  }

  /**
   * Core Dijkstra algorithm (unchanged logic, extracted for clarity).
   * 
   * Optimization opportunities in this method:
   * 1. PriorityQueue is allocated fresh each time - consider object pooling for high-frequency scenarios
   * 2. HashMap allocations for dist/previous maps - unavoidable for correctness
   * 3. Edge iteration now benefits from O(1) lookups in optimized CityGraph
   */
  private List<String> computeShortestPathDijkstra(CityGraph graph, String sourceNodeId, String targetNodeId, RoutingProfile routingProfile) {
    Map<String, Double> dist = new HashMap<>();
    Map<String, String> previous = new HashMap<>();
    PriorityQueue<NodeDistance> queue = new PriorityQueue<>();

    dist.put(sourceNodeId, 0.0);
    queue.add(new NodeDistance(sourceNodeId, 0.0));

    while (!queue.isEmpty()) {
      NodeDistance current = queue.poll();
      if (current.distance > dist.getOrDefault(current.nodeId, Double.MAX_VALUE)) {
        continue;
      }
      if (current.nodeId.equals(targetNodeId)) {
        break;
      }

      // OPTIMIZATION: CityGraph.getNeighbors() now returns edges with O(1) access pattern
      for (Edge edge : graph.getNeighbors(current.nodeId)) {
        double weight = computeEdgeCost(edge, routingProfile);
        if (weight < 0) {
          throw new IllegalStateException("edge weight must be non-negative");
        }

        double newDist = current.distance + weight;
        double existingDist = dist.getOrDefault(edge.getToNodeId(), Double.MAX_VALUE);

        if (newDist < existingDist) {
          dist.put(edge.getToNodeId(), newDist);
          previous.put(edge.getToNodeId(), current.nodeId);
          queue.add(new NodeDistance(edge.getToNodeId(), newDist));
        }
      }
    }

    if (!dist.containsKey(targetNodeId)) {
      return Collections.emptyList();
    }

    ArrayDeque<String> path = new ArrayDeque<>();
    String step = targetNodeId;
    while (step != null) {
      path.addFirst(step);
      step = previous.get(step);
    }

    if (path.isEmpty() || !path.peekFirst().equals(sourceNodeId) || !path.peekLast().equals(targetNodeId)) {
      return Collections.emptyList();
    }

    return new ArrayList<>(path);
  }

  public double computePathCost(CityGraph graph, List<String> path) {
    return computePathCost(graph, path, RoutingProfile.defaultProfile(1.0));
  }

  public double computePathCost(CityGraph graph, List<String> path, double tollPenalty) {
    return computePathCost(graph, path, RoutingProfile.defaultProfile(tollPenalty));
  }

  public double computePathCost(CityGraph graph, List<String> path, RoutingProfile routingProfile) {
    Objects.requireNonNull(graph, "graph must not be null");
    Objects.requireNonNull(path, "path must not be null");
    Objects.requireNonNull(routingProfile, "routingProfile must not be null");
    if (path.isEmpty() || path.size() == 1) {
      return 0.0;
    }

    double total = 0.0;
    for (int i = 0; i < path.size() - 1; i++) {
      String from = path.get(i);
      String to = path.get(i + 1);
      if (from.equals(to)) {
        continue;
      }
      Edge edge = graph.getEdge(from, to);
      if (edge == null) {
        throw new IllegalArgumentException("No direct edge between " + from + " and " + to);
      }
      total += computeEdgeCost(edge, routingProfile);
    }
    return total;
  }

  /**
   * OPTIMIZATION: Cache Invalidation
   * 
   * Call this method when traffic factors change significantly.
   * Increments the generation counter, making all old cache entries stale.
   * New Dijkstra computations will use the new traffic state.
   */
  public void invalidateRouteCache() {
    trafficGeneration.incrementAndGet();
  }

  /**
   * OPTIMIZATION: Manual Cache Control
   * 
   * For debugging or testing: clear entire cache.
   */
  public void clearRouteCache() {
    routeCache.clear();
  }

  /**
   * Returns current cache statistics (for monitoring).
   */
  public int getCacheSizeMetrics() {
    return routeCache.size();
  }

  /**
   * Edge weight for shortest-path: tolls plus fuel/wear where traffic multiplies effective fuel burn
   * (distance × baseFuelPerKm × trafficFactor), matching the urban logistics spec.
   */
  private double computeEdgeCost(Edge edge, RoutingProfile routingProfile) {
    double tollCost = edge.getTollCost() * routingProfile.tollPenalty() * routingProfile.tollWeightMultiplier();
    return edge.getDistanceKm() * edge.getTrafficFactor() * routingProfile.distanceCostMultiplier() + tollCost;
  }

  /**
   * OPTIMIZATION: Cache Key Generation
   * 
   * Format: sourceNodeId|targetNodeId|trafficGeneration
   * Simple string concatenation is faster than Object.hash() for this use case.
   */
  private String buildCacheKey(String sourceNodeId, String targetNodeId, long generation) {
    return sourceNodeId + "|" + targetNodeId + "|" + generation;
  }

  /**
   * Simple cache entry wrapper to store paths with metadata.
   */
  private static final class CachedRoute {
    private final List<String> path;
    private final long cachedAtNanos;

    CachedRoute(List<String> path, long cachedAtNanos) {
      this.path = Collections.unmodifiableList(new ArrayList<>(path));
      this.cachedAtNanos = cachedAtNanos;
    }
  }

  public record RoutingProfile(
      double distanceCostMultiplier,
      double tollPenalty,
      double tollWeightMultiplier,
      double fuelCostMultiplier) {

    public RoutingProfile {
      if (distanceCostMultiplier < 0.0) {
        throw new IllegalArgumentException("distanceCostMultiplier must be non-negative");
      }
      if (tollPenalty < 0.0) {
        throw new IllegalArgumentException("tollPenalty must be non-negative");
      }
      if (tollWeightMultiplier < 0.0) {
        throw new IllegalArgumentException("tollWeightMultiplier must be non-negative");
      }
      if (fuelCostMultiplier < 0.0) {
        throw new IllegalArgumentException("fuelCostMultiplier must be non-negative");
      }
    }

    public static RoutingProfile defaultProfile(double tollPenalty) {
      return new RoutingProfile(1.0, tollPenalty, 1.0, DEFAULT_FUEL_COST_MULTIPLIER);
    }
  }

  private static final class NodeDistance implements Comparable<NodeDistance> {
    private final String nodeId;
    private final double distance;

    NodeDistance(String nodeId, double distance) {
      this.nodeId = nodeId;
      this.distance = distance;
    }

    @Override
    public int compareTo(NodeDistance other) {
      return Double.compare(this.distance, other.distance);
    }
  }
}
