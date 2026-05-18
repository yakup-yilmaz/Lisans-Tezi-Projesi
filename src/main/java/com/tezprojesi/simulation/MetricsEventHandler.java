package com.tezprojesi.simulation;

import com.tezprojesi.events.DeliveryCompletedEvent;
import com.tezprojesi.events.Event;
import com.tezprojesi.events.EventBus;
import com.tezprojesi.events.OrderAssignedEvent;
import com.tezprojesi.events.VehicleAssignedEvent;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;

public final class MetricsEventHandler {

  private static final long DELAY_THRESHOLD_TICKS = 10;

  private final AtomicInteger totalOrders = new AtomicInteger();
  private final AtomicInteger completedOrders = new AtomicInteger();
  private final AtomicLong totalDeliveryTicks = new AtomicLong();
  private final AtomicLong totalAssignedOrders = new AtomicLong();
  private final DoubleAdder totalDistance = new DoubleAdder();

  private final Set<String> recordedOrderIds = ConcurrentHashMap.newKeySet();
  private final Set<String> recordedCreatedOrderIds = ConcurrentHashMap.newKeySet();

  private final ConcurrentMap<String, DoubleAdder> distancePerVehicle = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, LongAdder> utilizationPerVehicle = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, LongAdder> deliveriesPerVehicle = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, AtomicInteger> depotDispatchCount = new ConcurrentHashMap<>();
  private final AtomicInteger delayedOrderCount = new AtomicInteger();
  private final ConcurrentMap<String, LongAdder> depotStockChange = new ConcurrentHashMap<>();
  private final AtomicInteger fulfilledFromMicroCount = new AtomicInteger();


  public MetricsEventHandler(EventBus eventBus) {
    eventBus.subscribe(this::handleEvent);
  }

  private void handleEvent(Event event) {
    if (event instanceof OrderAssignedEvent assigned) {
      totalAssignedOrders.incrementAndGet();
      depotDispatchCount.computeIfAbsent(assigned.getDepotId(), id -> new AtomicInteger()).incrementAndGet();
    } else if (event instanceof VehicleAssignedEvent assigned) {
      // placeholder for event audit
    } else if (event instanceof DeliveryCompletedEvent completed) {
      if (!recordedOrderIds.add(completed.getOrderId())) {
        return;
      }
      completedOrders.incrementAndGet();
      long duration = completed.getDeliveryDurationTicks();
      totalDeliveryTicks.addAndGet(duration);

      deliveriesPerVehicle.computeIfAbsent(completed.getVehicleId(), id -> new LongAdder()).increment();
      utilizationPerVehicle.computeIfAbsent(completed.getVehicleId(), id -> new LongAdder()).add(duration);

      if (duration > DELAY_THRESHOLD_TICKS) {
        delayedOrderCount.incrementAndGet();
      }
    }
  }

  public void recordDistance(String vehicleId, double distanceKm) {
    if (vehicleId == null || vehicleId.isEmpty()) {
      throw new IllegalArgumentException("vehicleId must not be null or empty");
    }
    if (distanceKm < 0) {
      throw new IllegalArgumentException("distanceKm must be non-negative");
    }
    totalDistance.add(distanceKm);
    distancePerVehicle.computeIfAbsent(vehicleId, id -> new DoubleAdder()).add(distanceKm);
  }

  public void recordDepotStockChange(String depotId, double quantityChange) {
    if (depotId == null || depotId.isEmpty()) {
      throw new IllegalArgumentException("depotId must not be null or empty");
    }
    depotStockChange.computeIfAbsent(depotId, id -> new LongAdder()).add((long) quantityChange);
  }

  public void recordDepotDispatch(String depotId) {
    if (depotId == null || depotId.isEmpty()) {
      throw new IllegalArgumentException("depotId must not be null or empty");
    }
    depotDispatchCount.computeIfAbsent(depotId, id -> new AtomicInteger()).incrementAndGet();
  }

  public void recordVehicleUtilizationTick(String vehicleId, long tickCount) {
    if (vehicleId == null || vehicleId.isEmpty()) {
      throw new IllegalArgumentException("vehicleId must not be null or empty");
    }
    if (tickCount < 0) {
      throw new IllegalArgumentException("tickCount must be non-negative");
    }
    utilizationPerVehicle.computeIfAbsent(vehicleId, id -> new LongAdder()).add(tickCount);
  }

  public void recordFulfilledFromMicro(String orderId, long deliveryTicks) {
    if (!recordedOrderIds.add(orderId)) {
      return;
    }
    fulfilledFromMicroCount.incrementAndGet();
    completedOrders.incrementAndGet();
    long safeDeliveryTicks = Math.max(0, deliveryTicks);
    totalDeliveryTicks.addAndGet(safeDeliveryTicks);
    if (safeDeliveryTicks > DELAY_THRESHOLD_TICKS) {
      delayedOrderCount.incrementAndGet();
    }
  }

  public void recordCompletedOrder(String orderId, long deliveryTicks) {
    if (!recordedOrderIds.add(orderId)) {
      return;
    }
    long safeDeliveryTicks = Math.max(0, deliveryTicks);
    completedOrders.incrementAndGet();
    totalDeliveryTicks.addAndGet(safeDeliveryTicks);
    if (safeDeliveryTicks > DELAY_THRESHOLD_TICKS) {
      delayedOrderCount.incrementAndGet();
    }
  }

  public void recordOrderCreated(String orderId) {
    if (orderId == null || orderId.isBlank()) {
      throw new IllegalArgumentException("orderId must not be null or empty");
    }
    if (recordedCreatedOrderIds.add(orderId)) {
      totalOrders.incrementAndGet();
    }
  }

  public void reset() {
    totalOrders.set(0);
    completedOrders.set(0);
    totalDeliveryTicks.set(0);
    totalAssignedOrders.set(0);
    totalDistance.reset();
    recordedOrderIds.clear();
    recordedCreatedOrderIds.clear();
    distancePerVehicle.clear();
    utilizationPerVehicle.clear();
    deliveriesPerVehicle.clear();
    depotDispatchCount.clear();
    delayedOrderCount.set(0);
    depotStockChange.clear();
    fulfilledFromMicroCount.set(0);
  }

  public int getTotalOrders() {
    return totalOrders.get();
  }

  public int getCompletedOrders() {
    return completedOrders.get();
  }

  public long getTotalDeliveryTicks() {
    return totalDeliveryTicks.get();
  }

  public double getAverageDeliveryTicks() {
    int completed = completedOrders.get();
    return completed == 0 ? 0.0 : (double) totalDeliveryTicks.get() / completed;
  }

  public long getTotalAssignedOrders() {
    return totalAssignedOrders.get();
  }

  public double getTotalDistance() {
    return totalDistance.doubleValue();
  }

  public java.util.Map<String, Double> getDistancePerVehicle() {
    java.util.Map<String, Double> result = new java.util.HashMap<>();
    distancePerVehicle.forEach((vehicleId, adder) -> result.put(vehicleId, adder.doubleValue()));
    return java.util.Map.copyOf(result);
  }

  public java.util.Map<String, Long> getUtilizationPerVehicle() {
    java.util.Map<String, Long> result = new java.util.HashMap<>();
    utilizationPerVehicle.forEach((vehicleId, adder) -> result.put(vehicleId, adder.longValue()));
    return java.util.Map.copyOf(result);
  }

  public java.util.Map<String, Long> getDeliveriesPerVehicle() {
    java.util.Map<String, Long> result = new java.util.HashMap<>();
    deliveriesPerVehicle.forEach((vehicleId, adder) -> result.put(vehicleId, adder.longValue()));
    return java.util.Map.copyOf(result);
  }

  public java.util.Map<String, Integer> getDepotDispatchCount() {
    java.util.Map<String, Integer> result = new java.util.HashMap<>();
    depotDispatchCount.forEach((depotId, count) -> result.put(depotId, count.get()));
    return java.util.Map.copyOf(result);
  }

  public int getDelayedOrders() {
    return delayedOrderCount.get();
  }

  public java.util.Map<String, Long> getDepotStockChange() {
    java.util.Map<String, Long> result = new java.util.HashMap<>();
    depotStockChange.forEach((depotId, adder) -> result.put(depotId, adder.longValue()));
    return java.util.Map.copyOf(result);
  }

  public int getFulfilledFromMicroCount() {
    return fulfilledFromMicroCount.get();
  }
}
