package com.tezprojesi.simulation;

import com.tezprojesi.domain.OrderLine;
import com.tezprojesi.domain.ReplenishmentRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class ReplenishmentService {
  private final List<ReplenishmentRequest> requests = new ArrayList<>();

  public ReplenishmentRequest create(String sourceDepotId, String targetDepotId, List<OrderLine> items) {
    return create(sourceDepotId, targetDepotId, items, 0L);
  }

  public ReplenishmentRequest create(String sourceDepotId, String targetDepotId, List<OrderLine> items, long createdTick) {
    ReplenishmentRequest request = new ReplenishmentRequest(
        "repl-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
        sourceDepotId,
        targetDepotId,
        items,
        createdTick);
    requests.add(request);
    return request;
  }

  public ReplenishmentRequest createOrMerge(
      String sourceDepotId,
      String targetDepotId,
      List<OrderLine> items,
      long currentTick,
      long mergeWindowTicks) {
    Optional<ReplenishmentRequest> existing = requests.stream()
        .filter(request -> request.getStatus() == com.tezprojesi.domain.ReplenishmentStatus.CREATED)
        .filter(request -> request.getSourceDepotId().equals(sourceDepotId))
        .filter(request -> request.getTargetDepotId().equals(targetDepotId))
        .filter(request -> Math.abs(currentTick - request.getLastUpdatedTick()) <= mergeWindowTicks)
        .findFirst();

    if (existing.isPresent()) {
      existing.get().mergeItems(items, currentTick);
      return existing.get();
    }
    return create(sourceDepotId, targetDepotId, items, currentTick);
  }

  public List<ReplenishmentRequest> findAll() {
    return List.copyOf(new ArrayList<>(requests));
  }

  public Optional<ReplenishmentRequest> findById(String id) {
    return requests.stream().filter(request -> request.getId().equals(id)).findFirst();
  }

  public boolean hasPendingRequest(String targetDepotId, String productId) {
    return requests.stream().anyMatch(request ->
        request.getStatus() != com.tezprojesi.domain.ReplenishmentStatus.COMPLETED
            && request.getTargetDepotId().equals(targetDepotId)
            && request.getItems().stream().anyMatch(item -> item.getProductId().equals(productId)));
  }

  public boolean hasPendingRequestForDepot(String targetDepotId) {
    return requests.stream().anyMatch(request ->
        request.getStatus() != com.tezprojesi.domain.ReplenishmentStatus.COMPLETED
            && request.getTargetDepotId().equals(targetDepotId));
  }

  public int getIncomingQuantity(String targetDepotId, String productId) {
    return requests.stream()
        .filter(request -> request.getStatus() != com.tezprojesi.domain.ReplenishmentStatus.COMPLETED)
        .filter(request -> request.getTargetDepotId().equals(targetDepotId))
        .flatMap(request -> request.getItems().stream())
        .filter(item -> item.getProductId().equals(productId))
        .mapToInt(OrderLine::getQuantity)
        .sum();
  }

  public double getIncomingVolume(String targetDepotId) {
    return requests.stream()
        .filter(request -> request.getStatus() != com.tezprojesi.domain.ReplenishmentStatus.COMPLETED)
        .filter(request -> request.getTargetDepotId().equals(targetDepotId))
        .mapToDouble(ReplenishmentRequest::getTotalVolume)
        .sum();
  }

  public List<ReplenishmentRequest> findOpenRequests() {
    return requests.stream()
        .filter(request -> request.getStatus() != com.tezprojesi.domain.ReplenishmentStatus.COMPLETED)
        .toList();
  }

  /**
   * Calculate optimal replenishment quantity using advanced (s,S) policy.
   * 
   * Formula:
   * - effectiveGap = S - (currentStock + incomingQuantity)
   * - requestQuantity = max(0, effectiveGap)
   * - Can be capped by maxCapacity if provided (vehicle capacity limit)
   * 
   * @param currentStock Available stock at target depot
   * @param targetLevel S value (target stock level)
   * @param incomingQuantity Quantity already in transit
   * @param maxCapacity Optional upper limit (e.g., max vehicle capacity); 0 means no limit
   * @return Optimal quantity to request, or 0 if not needed
   */
  public int calculateOptimalRequestQuantity(
      int currentStock,
      int targetLevel,
      int incomingQuantity,
      int maxCapacity) {
    
    if (targetLevel <= 0 || currentStock < 0 || incomingQuantity < 0) {
      return 0;
    }

    // Calculate the gap: how much stock is needed to reach target level
    int effectiveGap = Math.max(0, targetLevel - (currentStock + incomingQuantity));
    
    if (effectiveGap <= 0) {
      return 0; // Already at or above target level
    }

    // Apply capacity limit if provided
    int requestQuantity = effectiveGap;
    if (maxCapacity > 0) {
      requestQuantity = Math.min(requestQuantity, maxCapacity);
    }

    return requestQuantity;
  }

  /**
   * Overload for backward compatibility: no capacity limit.
   */
  public int calculateOptimalRequestQuantity(
      int currentStock,
      int targetLevel,
      int incomingQuantity) {
    return calculateOptimalRequestQuantity(currentStock, targetLevel, incomingQuantity, 0);
  }

  public void clear() {
    requests.clear();
  }
}

