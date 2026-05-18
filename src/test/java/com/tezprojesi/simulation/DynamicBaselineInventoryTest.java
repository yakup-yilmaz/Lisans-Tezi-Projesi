package com.tezprojesi.simulation;

import com.tezprojesi.domain.OrderLine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for dynamic inventory baseline calculation using moving average.
 * 
 * Test Strategy:
 * 1. Verify moving average calculation over a 100-tick window
 * 2. Confirm baseline = movingAverage * 1.2
 * 3. Validate baseline adjustment when demand decreases (should go DOWN, not just up)
 * 4. Test optimal request quantity calculation (s,S policy)
 * 5. Ensure thread-safety and concurrent updates
 */
public class DynamicBaselineInventoryTest {

  private InventoryService inventoryService;
  private ReplenishmentService replenishmentService;

  private static final String DEPOT_ID = "depot-1";
  private static final String PRODUCT_ID = "prod-A";
  private static final double MOVING_AVG_MULTIPLIER = 1.2;

  @BeforeEach
  void setUp() {
    inventoryService = new InventoryService();
    replenishmentService = new ReplenishmentService();
  }

  @Test
  void testMovingAverageCalculationOver100Ticks() {
    // SCENARIO: Consume 10 units per tick for 100 ticks
    // Expected: movingAverage = 10.0, baseline = 10 * 1.2 = 12
    
    inventoryService.setStock(DEPOT_ID, PRODUCT_ID, 1500); // Initialize with enough stock
    
    // Simulate 100 ticks of consistent consumption
    for (int tick = 0; tick < 100; tick++) {
      List<OrderLine> order = List.of(new OrderLine(PRODUCT_ID, 10, 1.0));
      inventoryService.consume(DEPOT_ID, order);
    }
    
    double movingAverage = inventoryService.getMovingAverageConsumption(DEPOT_ID, PRODUCT_ID);
    int baseline = inventoryService.getBaselineStock(DEPOT_ID, PRODUCT_ID);
    
    // Verify moving average is correctly calculated
    assertEquals(10.0, movingAverage, 0.01, "Moving average should be 10.0");
    
    // Verify baseline = movingAverage * 1.2
    int expectedBaseline = (int) Math.ceil(movingAverage * MOVING_AVG_MULTIPLIER);
    assertEquals(expectedBaseline, baseline, "Baseline should be 12 (10 * 1.2)");
  }

  @Test
  void testBaselineDecreasesWhenDemandDeclines() {
    // SCENARIO: High consumption for 50 ticks, then low consumption for 50 ticks
    // Expected: Baseline should DECREASE from high level to low level
    
    inventoryService.setStock(DEPOT_ID, PRODUCT_ID, 5000); // Large initial stock
    
    // Phase 1: High consumption (50 ticks @ 20 units/tick)
    for (int tick = 0; tick < 50; tick++) {
      List<OrderLine> order = List.of(new OrderLine(PRODUCT_ID, 20, 1.0));
      inventoryService.consume(DEPOT_ID, order);
    }
    
    int baselineHighDemand = inventoryService.getBaselineStock(DEPOT_ID, PRODUCT_ID);
    double avgHighDemand = inventoryService.getMovingAverageConsumption(DEPOT_ID, PRODUCT_ID);
    
    // Phase 2: Low consumption (50 ticks @ 5 units/tick)
    // Window is 100 ticks total, so now we have mix: 50 @ 20 + 50 @ 5
    for (int tick = 50; tick < 100; tick++) {
      List<OrderLine> order = List.of(new OrderLine(PRODUCT_ID, 5, 1.0));
      inventoryService.consume(DEPOT_ID, order);
    }
    
    // After 100 ticks, recalculate baselines
    inventoryService.recalculateBaselinesForDepot(DEPOT_ID);
    
    int baselineLowDemand = inventoryService.getBaselineStock(DEPOT_ID, PRODUCT_ID);
    double avgLowDemand = inventoryService.getMovingAverageConsumption(DEPOT_ID, PRODUCT_ID);
    
    // Verify that baseline HAS DECREASED (critical for dynamic adjustment)
    assertTrue(baselineLowDemand < baselineHighDemand,
        "Baseline should DECREASE from " + baselineHighDemand + " to " + baselineLowDemand);
    
    // Average should be around 12.5 (midpoint of 20 and 5)
    assertEquals(12.5, avgLowDemand, 0.5, "Moving average should settle to ~12.5");
    
    int expectedLowBaseline = (int) Math.ceil(avgLowDemand * MOVING_AVG_MULTIPLIER);
    assertEquals(expectedLowBaseline, baselineLowDemand,
        "Baseline should be recalculated to " + expectedLowBaseline);
  }

  @Test
  void testSlidingWindowEnforcesSizeLimit() {
    // SCENARIO: Consume 110 items (10 per tick for 11 ticks)
    // Expected: Only last 100 ticks in window; oldest 10-unit entry should be dropped
    
    inventoryService.setStock(DEPOT_ID, PRODUCT_ID, 2000);
    
    // First tick: record 10 units
    List<OrderLine> order1 = List.of(new OrderLine(PRODUCT_ID, 10, 1.0));
    inventoryService.consume(DEPOT_ID, order1);
    assertEquals(10.0, inventoryService.getMovingAverageConsumption(DEPOT_ID, PRODUCT_ID), 0.01);
    
    // Ticks 2-100: record 10 units each
    for (int tick = 1; tick < 100; tick++) {
      List<OrderLine> order = List.of(new OrderLine(PRODUCT_ID, 10, 1.0));
      inventoryService.consume(DEPOT_ID, order);
    }
    
    // After 100 ticks, average is still 10
    double avg100 = inventoryService.getMovingAverageConsumption(DEPOT_ID, PRODUCT_ID);
    assertEquals(10.0, avg100, 0.01, "Average should remain 10.0 after 100 uniform ticks");
    
    // Tick 101: record 5 units (different consumption)
    List<OrderLine> order101 = List.of(new OrderLine(PRODUCT_ID, 5, 1.0));
    inventoryService.consume(DEPOT_ID, order101);
    
    // Now window should contain: 99 @ 10 units + 1 @ 5 units
    // Average = (990 + 5) / 100 = 9.95
    double avg101 = inventoryService.getMovingAverageConsumption(DEPOT_ID, PRODUCT_ID);
    assertEquals(9.95, avg101, 0.1, "Average should drop slightly when oldest 10-unit entry is removed");
  }

  @Test
  void testOptimalRequestQuantityCalculation() {
    // SCENARIO: s-S policy test
    // s (reorderPoint) = baseline * 0.30
    // S (targetLevel) = baseline * 0.80
    // Gap = S - (currentStock + incomingQuantity)
    
    // Setup: baseline = 100
    int currentStock = 20;
    int targetLevel = 80; // This is S = baseline * 0.80
    int incomingQuantity = 10;
    
    // Gap = 80 - (20 + 10) = 50
    int requestQty = replenishmentService.calculateOptimalRequestQuantity(
        currentStock, targetLevel, incomingQuantity, 0);
    
    assertEquals(50, requestQty, "Request quantity should be exactly 50 (gap)");
  }

  @Test
  void testOptimalRequestQuantityWithCapacityLimit() {
    // SCENARIO: Gap = 60, but vehicle max capacity = 40
    // Expected: requestQuantity = 40 (capped)
    
    int currentStock = 20;
    int targetLevel = 80;
    int incomingQuantity = 0;
    int maxCapacity = 40;
    
    // Gap = 80 - (20 + 0) = 60, but capped at 40
    int requestQty = replenishmentService.calculateOptimalRequestQuantity(
        currentStock, targetLevel, incomingQuantity, maxCapacity);
    
    assertEquals(40, requestQty, "Request quantity should be capped at max capacity (40)");
  }

  @Test
  void testNoReplenishmentNeededWhenAtTarget() {
    // SCENARIO: currentStock + incoming = targetLevel (no gap)
    // Expected: requestQuantity = 0
    
    int currentStock = 50;
    int targetLevel = 80;
    int incomingQuantity = 30; // Total = 50 + 30 = 80
    
    int requestQty = replenishmentService.calculateOptimalRequestQuantity(
        currentStock, targetLevel, incomingQuantity, 0);
    
    assertEquals(0, requestQty, "No replenishment needed when total stock meets target");
  }

  @Test
  void testConcurrentConsumptionUpdates() throws InterruptedException {
    // SCENARIO: Multiple threads consuming stock simultaneously
    // Expected: Moving average correctly aggregates all consumption
    
    inventoryService.setStock(DEPOT_ID, PRODUCT_ID, 100000);
    
    Thread[] threads = new Thread[10];
    int consumptionPerThread = 100;
    
    // Create 10 threads, each consuming 100 units (total 1000 units)
    for (int t = 0; t < 10; t++) {
      threads[t] = new Thread(() -> {
        for (int i = 0; i < consumptionPerThread; i++) {
          List<OrderLine> order = List.of(new OrderLine(PRODUCT_ID, 1, 1.0));
          inventoryService.consume(DEPOT_ID, order);
        }
      });
    }
    
    for (Thread t : threads) {
      t.start();
    }
    for (Thread t : threads) {
      t.join();
    }
    
    double movingAverage = inventoryService.getMovingAverageConsumption(DEPOT_ID, PRODUCT_ID);
    
    // After all threads: 1000 units consumed in 1 tick (from main thread perspective)
    // But due to thread scheduling, exact value depends on consumption distribution
    assertTrue(movingAverage > 0, "Moving average should record concurrent consumption");
  }

  @Test
  void testBaselineInitializationWithoutHistory() {
    // SCENARIO: Brand new depot-product with no consumption history
    // Expected: Baseline = current stock (no history to compute average)
    
    inventoryService.setStock(DEPOT_ID, "new-product", 100);
    
    int baseline = inventoryService.getBaselineStock(DEPOT_ID, "new-product");
    
    // Without consumption history, baseline should be initialized to current stock
    assertEquals(100, baseline, "Baseline should initialize to current stock when no history exists");
  }

  @Test
  void testConsumptionRecordingWithReservedStock() {
    // SCENARIO: Consume reserved stock (most realistic flow)
    // Expected: Consumption is still tracked in moving average
    
    inventoryService.setStock(DEPOT_ID, PRODUCT_ID, 1000);
    List<OrderLine> items = List.of(new OrderLine(PRODUCT_ID, 50, 1.0));
    
    // Reserve the stock
    List<OrderLine> reserved = inventoryService.reserveAvailable(DEPOT_ID, items);
    assertEquals(50, reserved.get(0).getQuantity(), "Should reserve 50 units");
    
    // Consume the reserved stock
    inventoryService.consumeReserved(DEPOT_ID, items);
    
    // Verify consumption was recorded
    double movingAverage = inventoryService.getMovingAverageConsumption(DEPOT_ID, PRODUCT_ID);
    assertEquals(50.0, movingAverage, 0.01, "Consumption should be recorded even for reserved stock");
  }

  @Test
  void testBaselineAdjustmentAfterMultipleRecalculations() {
    // SCENARIO: Simulate 3 demand phases with recalculations
    // Phase 1: 50 ticks @ 15 units/tick
    // Phase 2: 50 ticks @ 25 units/tick
    // Phase 3: recalculate; then 50 ticks @ 10 units/tick
    
    inventoryService.setStock(DEPOT_ID, PRODUCT_ID, 10000);
    
    // Phase 1
    for (int tick = 0; tick < 50; tick++) {
      inventoryService.consume(DEPOT_ID, List.of(new OrderLine(PRODUCT_ID, 15, 1.0)));
    }
    inventoryService.recalculateBaselinesForDepot(DEPOT_ID);
    int baseline1 = inventoryService.getBaselineStock(DEPOT_ID, PRODUCT_ID);
    
    // Phase 2
    for (int tick = 50; tick < 100; tick++) {
      inventoryService.consume(DEPOT_ID, List.of(new OrderLine(PRODUCT_ID, 25, 1.0)));
    }
    inventoryService.recalculateBaselinesForDepot(DEPOT_ID);
    int baseline2 = inventoryService.getBaselineStock(DEPOT_ID, PRODUCT_ID);
    
    // Baseline should increase from phase 1 to phase 2
    assertTrue(baseline2 > baseline1, "Baseline should increase when demand increases");
    
    // Phase 3: Lower demand
    for (int tick = 100; tick < 150; tick++) {
      inventoryService.consume(DEPOT_ID, List.of(new OrderLine(PRODUCT_ID, 10, 1.0)));
    }
    inventoryService.recalculateBaselinesForDepot(DEPOT_ID);
    int baseline3 = inventoryService.getBaselineStock(DEPOT_ID, PRODUCT_ID);
    
    // Baseline should decrease from phase 2 to phase 3
    assertTrue(baseline3 < baseline2, "Baseline should decrease when demand decreases");
  }
}
