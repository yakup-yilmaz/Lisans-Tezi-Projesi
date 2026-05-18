package com.tezprojesi.simulation;

import com.tezprojesi.routing.CityGraph;
import com.tezprojesi.routing.RoutingService;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OPTIMIZATION: Rush-Hour Traffic Modeling
 * 
 * Replaces uniform random traffic distribution with time-synchronized model.
 * 
 * Rush-Hour Model (Simulation Tick % 1440 = simulation minute of day):
 * - 1440 ticks = 24 hours * 60 minutes per hour
 * - Each tick represents 1 minute in simulation time
 * 
 * Time Zones:
 * - Night (0-300 ticks): 00:00-05:00, trafficFactor = 0.7 * jitter
 * - Morning Peak (480-600 ticks): 08:00-10:00, trafficFactor = 2.5 * jitter
 * - Afternoon (600-1020 ticks): 10:00-17:00, trafficFactor = 1.0-1.5 * jitter
 * - Evening Peak (1020-1140 ticks): 17:00-19:00, trafficFactor = 2.0 * jitter
 * - Night (1140-1440 ticks): 19:00-24:00, trafficFactor = 0.9 * jitter
 * 
 * Impact on simulation:
 * - Vehicles experience realistic travel times matching rush-hour patterns
 * - Route optimization dynamically adapts to time-based congestion
 * - Affects vehicle remainingTicks calculation in VehicleMovementService
 */
public final class TrafficManager {

  private final Random random;
  private final Map<String, Double> fixedTrafficFactors = new ConcurrentHashMap<>();
  
  /**
   * OPTIMIZATION: Reference to RoutingService for cache invalidation
   * 
   * When traffic factors change (each tick), invalidate route cache
   * to force recalculation with new traffic state.
   */
  private RoutingService routingService;

  public TrafficManager(Random random) {
    this.random = Objects.requireNonNull(random, "random must not be null");
  }

  /**
   * OPTIMIZATION: Setter for routing service reference
   * 
   * Called during initialization to enable cache invalidation
   * when traffic changes.
   */
  public void setRoutingService(RoutingService routingService) {
    this.routingService = routingService;
  }

  /**
   * LEGACY METHOD: Preserved for backward compatibility
   * 
   * Uses old uniform random distribution (non-time-based).
   * New code should use updateTrafficWithRushHourModel() instead.
   */
  public void updateTraffic(CityGraph graph, double minFactor, double maxFactor) {
    Objects.requireNonNull(graph, "graph must not be null");
    if (minFactor <= 0 || maxFactor <= minFactor) {
      throw new IllegalArgumentException("invalid traffic factor bounds");
    }

    for (String from : graph.getNodes()) {
      for (var edge : graph.getNeighbors(from)) {
        String key = edgeKey(edge.getFromNodeId(), edge.getToNodeId());
        if (fixedTrafficFactors.containsKey(key)) {
          continue;
        }
        double newFactor = minFactor + (random.nextDouble() * (maxFactor - minFactor));
        graph.updateTrafficFactor(edge.getFromNodeId(), edge.getToNodeId(), newFactor);
      }
    }
    
    // OPTIMIZATION: Invalidate route cache on traffic update
    if (routingService != null) {
      routingService.invalidateRouteCache();
    }
  }

  /**
   * OPTIMIZATION: Rush-Hour Traffic Model (NEW PRIMARY METHOD)
   * 
   * Replaces uniform random with time-synchronized traffic pattern.
   * Each road also has a deterministic "road character" bias, so that
   * some roads are naturally busier (main arteries) and some are quieter
   * (side streets), even at the same time of day.
   * 
   * Parameters:
   * - graph: CityGraph to update
   * - simulationTick: Current simulation tick (0-based, wraps at 1440)
   * 
   * Algorithm:
   * 1. Map tick to time of day (% 1440)
   * 2. Determine base traffic factor for time period
   * 3. Calculate per-edge "road bias" from edge identity hash
   * 4. Apply random jitter (+/- 15%) for realism
   * 5. Update all non-locked edges
   * 6. Invalidate route cache
   */
  public void updateTrafficWithRushHourModel(CityGraph graph, long simulationTick) {
    Objects.requireNonNull(graph, "graph must not be null");
    if (simulationTick < 0) {
      throw new IllegalArgumentException("simulationTick must be non-negative");
    }

    // Map tick to time of day (1440 ticks = 24 hours)
    int tickOfDay = (int) (simulationTick % 1440);

    // OPTIMIZATION: Calculate base traffic factor for current time period
    double baseTrafficFactor = calculateBaseTrafficFactor(tickOfDay);

    // Update all edges (except locked ones) with per-edge variation
    for (String from : graph.getNodes()) {
      for (var edge : graph.getNeighbors(from)) {
        String key = edgeKey(edge.getFromNodeId(), edge.getToNodeId());
        if (fixedTrafficFactors.containsKey(key)) {
          // Skip manually locked edges
          continue;
        }

        // OPTIMIZATION: Per-edge "road character" bias
        // Deterministic hash: same edge always gets the same bias
        // Bias range: 0.55 (quiet side street) to 1.55 (busy arterial)
        double roadBias = calculateRoadBias(edge.getFromNodeId(), edge.getToNodeId());

        // Per-edge jitter (+/- 15%) for realistic frame-to-frame variation
        double jitter = 0.85 + (random.nextDouble() * 0.30); // 0.85 to 1.15

        double trafficFactor = baseTrafficFactor * roadBias * jitter;

        // Ensure traffic factor stays in reasonable bounds
        trafficFactor = Math.max(0.5, Math.min(3.0, trafficFactor));

        graph.updateTrafficFactor(edge.getFromNodeId(), edge.getToNodeId(), trafficFactor);
      }
    }

    // OPTIMIZATION: Invalidate route cache after traffic update
    if (routingService != null) {
      routingService.invalidateRouteCache();
    }
  }

  /**
   * OPTIMIZATION: Per-road bias calculation
   * 
   * Uses a deterministic hash of the two node IDs to produce a stable
   * "road character" multiplier. Roads connecting major hubs (MAIN, M*)
   * tend toward higher bias (busier), while side streets get lower bias.
   * 
   * The hash is direction-independent: A→B and B→A get the same bias.
   * 
   * Returns a multiplier in range [0.55, 1.55].
   */
  private double calculateRoadBias(String fromNodeId, String toNodeId) {
    // Direction-independent: sort so A→B == B→A
    String a = fromNodeId.compareTo(toNodeId) < 0 ? fromNodeId : toNodeId;
    String b = fromNodeId.compareTo(toNodeId) < 0 ? toNodeId : fromNodeId;

    // Deterministic hash using prime multipliers
    long hash = 7;
    for (char c : a.toCharArray()) {
      hash = hash * 31 + c;
    }
    for (char c : b.toCharArray()) {
      hash = hash * 37 + c;
    }

    // Map to [0.0, 1.0]
    double normalized = Math.abs(Math.sin(hash * 0.00137)) ; // Stable, deterministic

    // Bonus: Roads involving MAIN or M* (depots) are inherently busier
    boolean isMajorRoad = fromNodeId.startsWith("M") || toNodeId.startsWith("M")
        || fromNodeId.equals("MAIN") || toNodeId.equals("MAIN");

    double base = 0.55 + normalized * 1.0; // [0.55, 1.55]

    if (isMajorRoad) {
      // Major roads: shift bias up (0.8 — 1.55 range)
      base = Math.max(0.8, base * 1.15);
      base = Math.min(1.55, base);
    }

    return base;
  }

  /**
   * OPTIMIZATION: Time-based Traffic Factor Calculation
   * 
   * Maps simulation time to realistic urban traffic patterns.
   * 
   * Time zones (tick % 1440):
   * - Tick 0-300 (00:00-05:00): Night - Low traffic
   * - Tick 300-480 (05:00-08:00): Early morning - Gradual increase
   * - Tick 480-600 (08:00-10:00): Morning Peak - Maximum
   * - Tick 600-1020 (10:00-17:00): Afternoon - Normal
   * - Tick 1020-1140 (17:00-19:00): Evening Peak - High
   * - Tick 1140-1440 (19:00-24:00): Night - Decreasing
   * 
   * Returns base traffic factor (before jitter).
   */
  private double calculateBaseTrafficFactor(int tickOfDay) {
    if (tickOfDay < 300) {
      // Night (00:00-05:00): Minimal traffic
      return 0.7;
    } else if (tickOfDay < 480) {
      // Early morning (05:00-08:00): Gradual increase
      // Linear interpolation from 0.7 to 2.5
      double progress = (tickOfDay - 300.0) / (480.0 - 300.0);
      return 0.7 + (progress * 1.8);
    } else if (tickOfDay < 600) {
      // Morning peak (08:00-10:00): Maximum traffic
      return 2.5;
    } else if (tickOfDay < 1020) {
      // Afternoon (10:00-17:00): Normal traffic
      return 1.0; // Standard conditions (1.0 = baseline)
    } else if (tickOfDay < 1140) {
      // Evening peak (17:00-19:00): High traffic
      return 2.0;
    } else {
      // Late night (19:00-24:00): Decreasing traffic
      // Linear interpolation from 2.0 to 0.9
      double progress = (tickOfDay - 1140.0) / (1440.0 - 1140.0);
      return 2.0 - (progress * 1.1);
    }
  }

  /**
   * OPTIMIZATION: Get Current Traffic State Description
   * 
   * For monitoring/debugging: returns human-readable traffic period name.
   */
  public String getCurrentTrafficPeriod(long simulationTick) {
    int tickOfDay = (int) (simulationTick % 1440);
    
    if (tickOfDay < 300) return "Night (Low)";
    if (tickOfDay < 480) return "Early Morning (Rising)";
    if (tickOfDay < 600) return "Morning Peak (High)";
    if (tickOfDay < 1020) return "Afternoon (Normal)";
    if (tickOfDay < 1140) return "Evening Peak (High)";
    return "Late Night (Decreasing)";
  }

  public void setFixedTrafficFactor(CityGraph graph, String fromNodeId, String toNodeId, double trafficFactor) {
    setManualTrafficFactorAndLock(graph, fromNodeId, toNodeId, trafficFactor);
  }

  public void setManualTrafficFactorAndLock(CityGraph graph, String fromNodeId, String toNodeId, double trafficFactor) {
    Objects.requireNonNull(graph, "graph must not be null");
    if (trafficFactor <= 0.0) {
      throw new IllegalArgumentException("trafficFactor must be positive");
    }
    graph.updateTrafficFactor(fromNodeId, toNodeId, trafficFactor);
    fixedTrafficFactors.put(edgeKey(fromNodeId, toNodeId), trafficFactor);
    
    // OPTIMIZATION: Invalidate route cache after manual change
    if (routingService != null) {
      routingService.invalidateRouteCache();
    }
  }

  public void clearFixedTrafficFactor(String fromNodeId, String toNodeId) {
    fixedTrafficFactors.remove(edgeKey(fromNodeId, toNodeId));
    
    // OPTIMIZATION: Invalidate route cache
    if (routingService != null) {
      routingService.invalidateRouteCache();
    }
  }

  public boolean hasFixedTrafficFactor(String fromNodeId, String toNodeId) {
    return fixedTrafficFactors.containsKey(edgeKey(fromNodeId, toNodeId));
  }

  private String edgeKey(String fromNodeId, String toNodeId) {
    return fromNodeId + "->" + toNodeId;
  }
}
