package com.tezprojesi.simulation;

import java.util.LinkedList;
import java.util.Queue;

/**
 * Tracks consumption history over a sliding window (default: 100 ticks).
 * Used for calculating dynamic moving-average baseline stocks.
 */
final class ConsumptionHistory {
  private final Queue<Integer> tickConsumptions = new LinkedList<>();
  private final int windowSize;
  private int totalConsumption = 0;

  ConsumptionHistory(int windowSize) {
    this.windowSize = windowSize > 0 ? windowSize : 100;
  }

  /**
   * Record consumption for a single tick (Thread-Safe).
   */
  synchronized void recordConsumption(int quantity) {
    if (quantity < 0) {
      throw new IllegalArgumentException("Consumption cannot be negative");
    }
    tickConsumptions.offer(quantity);
    totalConsumption += quantity;
    
    // Enforce sliding window: drop oldest entry if over limit
    while (tickConsumptions.size() > windowSize) {
      Integer oldest = tickConsumptions.poll();
      totalConsumption -= (oldest != null ? oldest : 0);
    }
  }

  /**
   * Get the average consumption per tick over the window (Thread-Safe).
   */
  synchronized double getMovingAverage() {
    if (tickConsumptions.isEmpty()) {
      return 0.0;
    }
    return totalConsumption / (double) tickConsumptions.size();
  }

  /**
   * Get the total consumption in the current window (Thread-Safe).
   */
  synchronized int getTotalConsumption() {
    return totalConsumption;
  }

  /**
   * Get the number of ticks recorded in the window (Thread-Safe).
   */
  synchronized int getWindowFillCount() {
    return tickConsumptions.size();
  }

  /**
   * Clear history on reset (Thread-Safe).
   */
  synchronized void clear() {
    tickConsumptions.clear();
    totalConsumption = 0;
  }
}
