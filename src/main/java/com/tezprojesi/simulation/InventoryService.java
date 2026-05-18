package com.tezprojesi.simulation;

import com.tezprojesi.domain.OrderLine;
import com.tezprojesi.domain.Product;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class InventoryService {
  /** Micro depots: max physical volume (m³) per master spec. */
  public static final double MICRO_DEPOT_MAX_VOLUME = 100.0;

  private final Map<String, Map<String, Integer>> stockByDepot = new ConcurrentHashMap<>();
  private final Map<String, Map<String, Integer>> baselineStockByDepot = new ConcurrentHashMap<>();
  private final Map<String, Map<String, Integer>> reservedStockByDepot = new ConcurrentHashMap<>();
  /** Tick when stock for a SKU was last increased at a depot (wait-time inflation anchor). */
  private final Map<String, Map<String, Long>> stockSinceTickByDepot = new ConcurrentHashMap<>();
  /** Current simulation tick — set by SimulationEngine each step. */
  private volatile long currentSimulationTick = 0L;
  /**
   * Per-depot, per-product consumption history for moving-average baseline calculation.
   * Tracks consumption over a sliding window (default: 100 ticks).
   */
  private final Map<String, Map<String, ConsumptionHistory>> consumptionHistoryByDepot = new ConcurrentHashMap<>();
  
  /**
   * Window size (in ticks) for moving average consumption calculation.
   * Default: 100 ticks. Baseline = movingAverage * 1.2
   */
  private static final int CONSUMPTION_WINDOW_SIZE = 100;
  private static final double MOVING_AVERAGE_MULTIPLIER = 1.2;

  public Map<String, Map<String, Integer>> snapshot() {
    Map<String, Map<String, Integer>> result = new HashMap<>();
    stockByDepot.forEach((depotId, items) -> result.put(depotId, new HashMap<>(items)));
    // Snapshot return type only captures total stock. Reserved stock is intentionally
    // not serialized here, and restore() resets reservations to a clean state.
    return Map.copyOf(result);
  }

  public Map<String, Integer> getDepotStock(String depotId) {
    return Map.copyOf(stockByDepot.getOrDefault(depotId, Map.of()));
  }

  public Map<String, Integer> getReservedStockByDepot(String depotId) {
    return Map.copyOf(reservedStockByDepot.getOrDefault(depotId, Map.of()));
  }

  @Deprecated
  public Map<String, Integer> getReservedStock(String depotId) {
    return getReservedStockByDepot(depotId);
  }

  public void setSimulationClock(long tick) {
    this.currentSimulationTick = Math.max(0L, tick);
  }

  public long getStockWaitTicks(String depotId, String productId, long currentTick) {
    long since = stockSinceTickByDepot.getOrDefault(depotId, Map.of()).getOrDefault(productId, 0L);
    return Math.max(0L, currentTick - since);
  }

  /**
   * Ensures inbound replenishment will not exceed {@link #MICRO_DEPOT_MAX_VOLUME} m³ at a micro depot.
   */
  public void validateMicroDepotInboundCapacity(
      String targetDepotId,
      List<OrderLine> inboundItems,
      ProductCatalogService catalog,
      boolean targetIsMicroDepot,
      double incomingVolume) {
    if (!targetIsMicroDepot || inboundItems == null || inboundItems.isEmpty()) {
      return;
    }
    double current = computeTotalVolume(targetDepotId, catalog);
    double add = inboundItems.stream().mapToDouble(OrderLine::getLineVolume).sum();
    if (current + incomingVolume + add > MICRO_DEPOT_MAX_VOLUME + 1e-6) {
      throw new IllegalStateException(String.format(
          java.util.Locale.US,
          "Mikro depo kapasitesi asildi: mevcut stok %.2f m3 + yoldaki ikmal %.2f m3 + yeni talep %.2f m3 > limit %.0f m3 (depo %s).",
          current,
          incomingVolume,
          add,
          MICRO_DEPOT_MAX_VOLUME,
          targetDepotId));
    }
  }

  public double computeTotalVolume(String depotId, ProductCatalogService catalog) {
    Map<String, Integer> stock = stockByDepot.getOrDefault(depotId, Map.of());
    double total = 0.0;
    for (Map.Entry<String, Integer> e : stock.entrySet()) {
      double uv = catalog.findById(e.getKey()).map(Product::getUnitVolume).orElse(0.0);
      total += uv * e.getValue();
    }
    return total;
  }

  public void setStock(String depotId, String productId, int quantity) {
    if (quantity < 0) {
      throw new IllegalArgumentException("quantity cannot be negative");
    }
    Map<String, Integer> stock = stockByDepot.computeIfAbsent(depotId, key -> new ConcurrentHashMap<>());
    int prev = stock.getOrDefault(productId, 0);
    stock.put(productId, quantity);
    if (quantity > prev) {
      stockSinceTickByDepot.computeIfAbsent(depotId, k -> new ConcurrentHashMap<>()).put(productId, currentSimulationTick);
    }
    updateBaselineFromMovingAverage(depotId, productId);
  }

  public void addStock(String depotId, String productId, int quantity) {
    if (quantity < 0) {
      throw new IllegalArgumentException("quantity cannot be negative");
    }
    if (quantity > 0) {
      stockSinceTickByDepot.computeIfAbsent(depotId, k -> new ConcurrentHashMap<>()).put(productId, currentSimulationTick);
    }
    Map<String, Integer> stock = stockByDepot.computeIfAbsent(depotId, key -> new ConcurrentHashMap<>());
    stock.merge(productId, quantity, Integer::sum);
    updateBaselineFromMovingAverage(depotId, productId);
  }

  public void addStockWithLimit(String depotId, String productId, int quantity) {
    if (quantity < 0) {
      throw new IllegalArgumentException("quantity cannot be negative");
    }
    int current = getTotalStock(depotId, productId);
    int allowed = DispatchConstants.MAX_PRODUCT_CAPACITY - current;
    if (quantity > allowed) {
      throw new IllegalStateException(String.format(
          java.util.Locale.US,
          "Kapasite yetersiz. Mevcut: %d, Max: %d. En fazla +%d adet ekleyebilirsiniz.",
          current, DispatchConstants.MAX_PRODUCT_CAPACITY, Math.max(0, allowed)));
    }
    addStock(depotId, productId, quantity);
  }



  public boolean isBelowThreshold(String depotId, String productId, double fraction) {
    int current = getAvailableStock(depotId, productId);
    int baseline = baselineStockByDepot.getOrDefault(depotId, Map.of()).getOrDefault(productId, current);
    if (baseline <= 0) {
      return false;
    }
    return current <= Math.floor(baseline * fraction);
  }

  public int desiredTopUpQuantity(String depotId, String productId) {
    int current = getAvailableStock(depotId, productId);
    int baseline = baselineStockByDepot.getOrDefault(depotId, Map.of()).getOrDefault(productId, current);
    return Math.max(0, baseline - current);
  }

  public int getBaselineStock(String depotId, String productId) {
    return baselineStockByDepot.getOrDefault(depotId, Map.of()).getOrDefault(productId, 0);
  }

  public int getTotalStock(String depotId, String productId) {
    return stockByDepot.getOrDefault(depotId, Map.of()).getOrDefault(productId, 0);
  }

  public int getReservedStock(String depotId, String productId) {
    return reservedStockByDepot.getOrDefault(depotId, Map.of()).getOrDefault(productId, 0);
  }

  public int getAvailableStock(String depotId, String productId) {
    return Math.max(0, getTotalStock(depotId, productId) - getReservedStock(depotId, productId));
  }

  public boolean hasEnough(String depotId, List<OrderLine> items) {
    Map<String, Integer> stock = stockByDepot.getOrDefault(depotId, Map.of());
    return items.stream().allMatch(item -> stock.getOrDefault(item.getProductId(), 0) >= item.getQuantity());
  }

  public boolean hasEnoughAvailable(String depotId, List<OrderLine> items) {
    return items.stream().allMatch(item -> getAvailableStock(depotId, item.getProductId()) >= item.getQuantity());
  }

  public List<OrderLine> reserveAvailable(String depotId, List<OrderLine> items) {
    Map<String, Integer> reserved = reservedStockByDepot.computeIfAbsent(depotId, key -> new ConcurrentHashMap<>());
    List<OrderLine> result = new java.util.ArrayList<>();
    for (OrderLine item : items) {
      int available = getAvailableStock(depotId, item.getProductId());
      int quantityToReserve = Math.min(available, item.getQuantity());
      if (quantityToReserve <= 0) {
        continue;
      }
      reserved.merge(item.getProductId(), quantityToReserve, Integer::sum);
      result.add(new OrderLine(item.getProductId(), quantityToReserve, item.getUnitVolume()));
    }
    return List.copyOf(result);
  }

  public void releaseReservation(String depotId, List<OrderLine> items) {
    Map<String, Integer> reserved = reservedStockByDepot.computeIfAbsent(depotId, key -> new ConcurrentHashMap<>());
    for (OrderLine item : items) {
      int currentReserved = reserved.getOrDefault(item.getProductId(), 0);
      int nextReserved = Math.max(0, currentReserved - item.getQuantity());
      if (nextReserved == 0) {
        reserved.remove(item.getProductId());
      } else {
        reserved.put(item.getProductId(), nextReserved);
      }
    }
  }

  public void consumeReserved(String depotId, List<OrderLine> items) {
    Map<String, Integer> stock = stockByDepot.computeIfAbsent(depotId, key -> new ConcurrentHashMap<>());
    Map<String, Integer> reserved = reservedStockByDepot.computeIfAbsent(depotId, key -> new ConcurrentHashMap<>());
    for (OrderLine item : items) {
      int currentReserved = reserved.getOrDefault(item.getProductId(), 0);
      if (currentReserved < item.getQuantity()) {
        throw new IllegalStateException("reserved stock is insufficient for depot " + depotId + " product " + item.getProductId());
      }
      int currentStock = stock.getOrDefault(item.getProductId(), 0);
      if (currentStock < item.getQuantity()) {
        throw new IllegalStateException("stock is insufficient for depot " + depotId + " product " + item.getProductId());
      }
      int nextReserved = currentReserved - item.getQuantity();
      int nextStock = currentStock - item.getQuantity();
      if (nextReserved == 0) {
        reserved.remove(item.getProductId());
      } else {
        reserved.put(item.getProductId(), nextReserved);
      }
      stock.put(item.getProductId(), nextStock);
      // Record consumption for moving average tracking
      recordConsumption(depotId, item.getProductId(), item.getQuantity());
    }
  }

  public void consume(String depotId, List<OrderLine> items) {
    if (!hasEnough(depotId, items)) {
      throw new IllegalStateException("insufficient stock for depot " + depotId);
    }
    Map<String, Integer> stock = stockByDepot.computeIfAbsent(depotId, key -> new ConcurrentHashMap<>());
    Map<String, Integer> reserved = reservedStockByDepot.computeIfAbsent(depotId, key -> new ConcurrentHashMap<>());
    for (OrderLine item : items) {
      stock.put(item.getProductId(), stock.getOrDefault(item.getProductId(), 0) - item.getQuantity());
      int currentReserved = reserved.getOrDefault(item.getProductId(), 0);
      int toRelease = Math.min(currentReserved, item.getQuantity());
      if (toRelease > 0) {
        int nextReserved = currentReserved - toRelease;
        if (nextReserved == 0) {
          reserved.remove(item.getProductId());
        } else {
          reserved.put(item.getProductId(), nextReserved);
        }
      }
      // Record consumption for moving average tracking
      recordConsumption(depotId, item.getProductId(), item.getQuantity());
    }
  }

  public void consumeManually(String depotId, String productId, int quantity) {
    if (quantity <= 0) {
      throw new IllegalArgumentException("quantity must be positive");
    }
    int current = getTotalStock(depotId, productId);
    if (current < quantity) {
      throw new IllegalStateException("Yetersiz stok. Mevcut: " + current);
    }
    Map<String, Integer> stock = stockByDepot.computeIfAbsent(depotId, key -> new ConcurrentHashMap<>());
    stock.put(productId, current - quantity);
    recordConsumption(depotId, productId, quantity);
  }

  public void addItems(String depotId, List<OrderLine> items) {
    for (OrderLine item : items) {
      addStock(depotId, item.getProductId(), item.getQuantity());
    }
  }

  public List<OrderLine> calculateShortage(String depotId, List<OrderLine> items) {
    Map<String, Integer> stock = stockByDepot.getOrDefault(depotId, Map.of());
    return items.stream()
        .filter(item -> stock.getOrDefault(item.getProductId(), 0) < item.getQuantity())
        .map(item -> new OrderLine(
            item.getProductId(),
            item.getQuantity() - stock.getOrDefault(item.getProductId(), 0),
            item.getUnitVolume()))
        .toList();
  }

  public List<OrderLine> calculateAvailableShortage(String depotId, List<OrderLine> items) {
    return items.stream()
        .filter(item -> getAvailableStock(depotId, item.getProductId()) < item.getQuantity())
        .map(item -> new OrderLine(
            item.getProductId(),
            item.getQuantity() - getAvailableStock(depotId, item.getProductId()),
            item.getUnitVolume()))
        .toList();
  }

  public void clear() {
    stockByDepot.clear();
    baselineStockByDepot.clear();
    reservedStockByDepot.clear();
    stockSinceTickByDepot.clear();
    consumptionHistoryByDepot.values().forEach(depotHistory ->
        depotHistory.values().forEach(ConsumptionHistory::clear));
    consumptionHistoryByDepot.clear();
  }

  /**
   * Record consumption for a given depot and product.
   * Used internally by consume() and consumeReserved() to track moving average.
   */
  private void recordConsumption(String depotId, String productId, int quantity) {
    if (quantity <= 0) {
      return; // No consumption to record
    }
    Map<String, ConsumptionHistory> depotHistory = consumptionHistoryByDepot.computeIfAbsent(depotId, key -> new ConcurrentHashMap<>());
    ConsumptionHistory productHistory = depotHistory.computeIfAbsent(productId, key -> new ConsumptionHistory(CONSUMPTION_WINDOW_SIZE));
    productHistory.recordConsumption(quantity);
  }

  /**
   * Update baseline stock based on moving average consumption.
   * Formula: baseline = movingAverage * MOVING_AVERAGE_MULTIPLIER (default: 1.2)
   * If no consumption history available, initialize baseline to current stock.
   */
  private void updateBaselineFromMovingAverage(String depotId, String productId) {
    Map<String, ConsumptionHistory> depotHistory = consumptionHistoryByDepot.getOrDefault(depotId, Map.of());
    ConsumptionHistory productHistory = depotHistory.get(productId);

    int newBaseline;
    if (productHistory != null && productHistory.getWindowFillCount() >= 1) {
      // Calculate baseline from moving average
      double movingAvg = productHistory.getMovingAverage();
      newBaseline = Math.max(1, (int) Math.ceil(movingAvg * MOVING_AVERAGE_MULTIPLIER));
    } else {
      // No consumption history yet; initialize to current stock
      int currentStock = getTotalStock(depotId, productId);
      newBaseline = Math.max(1, currentStock);
    }

    baselineStockByDepot.computeIfAbsent(depotId, key -> new ConcurrentHashMap<>()).put(productId, newBaseline);
  }

  /**
   * Get the moving average consumption for a depot-product pair over the consumption window.
   * Useful for diagnostics and testing.
   */
  public double getMovingAverageConsumption(String depotId, String productId) {
    Map<String, ConsumptionHistory> depotHistory = consumptionHistoryByDepot.getOrDefault(depotId, Map.of());
    ConsumptionHistory productHistory = depotHistory.get(productId);
    if (productHistory == null) {
      return 0.0;
    }
    return productHistory.getMovingAverage();
  }

  /**
   * Recalculate all baselines for a depot based on current moving averages.
   * Called periodically (e.g., every N ticks) to adjust to changing demand patterns.
   */
  public void recalculateBaselinesForDepot(String depotId) {
    Map<String, Integer> currentStock = stockByDepot.getOrDefault(depotId, Map.of());
    currentStock.keySet().forEach(productId -> updateBaselineFromMovingAverage(depotId, productId));
  }

  public void restore(Map<String, Map<String, Integer>> snapshot) {
    clear();
    snapshot.forEach((depotId, items) -> items.forEach((productId, quantity) -> setStock(depotId, productId, quantity)));
  }
}
