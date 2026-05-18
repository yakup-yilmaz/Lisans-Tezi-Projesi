package com.tezprojesi.api.controller;

import com.tezprojesi.api.dto.ReplenishmentDto;
import com.tezprojesi.api.dto.ReplenishmentRequestDto;
import com.tezprojesi.api.dto.VehicleReplenishmentGroupDto;
import com.tezprojesi.api.exception.BadRequestException;
import com.tezprojesi.api.mapper.DomainToApiMapper;
import com.tezprojesi.domain.OrderLine;
import com.tezprojesi.domain.ReplenishmentRequest;
import com.tezprojesi.domain.Vehicle;
import com.tezprojesi.simulation.InventoryService;
import com.tezprojesi.simulation.ProductCatalogService;
import com.tezprojesi.simulation.ReplenishmentService;
import com.tezprojesi.simulation.SimulationEngine;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/replenishments")
public class ReplenishmentController {
  private final ReplenishmentService replenishmentService;
  private final ProductCatalogService productCatalogService;
  private final InventoryService inventoryService;
  private final SimulationEngine simulationEngine;

  public ReplenishmentController(
      ReplenishmentService replenishmentService,
      ProductCatalogService productCatalogService,
      InventoryService inventoryService,
      SimulationEngine simulationEngine) {
    this.replenishmentService = Objects.requireNonNull(replenishmentService);
    this.productCatalogService = Objects.requireNonNull(productCatalogService);
    this.inventoryService = Objects.requireNonNull(inventoryService);
    this.simulationEngine = Objects.requireNonNull(simulationEngine);
  }

  @GetMapping
  public ResponseEntity<List<ReplenishmentDto>> listReplenishments() {
    return ResponseEntity.ok(replenishmentService.findAll().stream().map(DomainToApiMapper::toReplenishmentDto).toList());
  }

  @GetMapping("/by-vehicle")
  public ResponseEntity<List<VehicleReplenishmentGroupDto>> listReplenishmentsByVehicle() {
    List<ReplenishmentDto> allReplenishments = replenishmentService.findAll().stream().map(DomainToApiMapper::toReplenishmentDto).toList();
    Map<String, List<ReplenishmentDto>> groupedByVehicle = allReplenishments.stream()
        .filter(rep -> rep.getAssignedVehicleId() != null && !rep.getAssignedVehicleId().isEmpty())
        .collect(Collectors.groupingBy(ReplenishmentDto::getAssignedVehicleId));

    List<VehicleReplenishmentGroupDto> result = new ArrayList<>();
    for (String vehicleId : groupedByVehicle.keySet()) {
      Vehicle vehicle = simulationEngine.getVehicles().stream()
          .filter(v -> v.getId().equals(vehicleId))
          .findFirst()
          .orElse(null);
      if (vehicle == null) {
        continue;
      }
      List<ReplenishmentDto> reps = groupedByVehicle.get(vehicleId);
      double totalVolume = reps.stream().mapToDouble(ReplenishmentDto::getTotalVolume).sum();
      result.add(new VehicleReplenishmentGroupDto(
          vehicleId,
          vehicle.getStatus().toString(),
          vehicle.getCapacityVolume(),
          vehicle.getCurrentLoad(),
          vehicle.getLoadFraction(),
          vehicle.getPlateNumber(),
          vehicle.getDriverName(),
          vehicle.getFuelType(),
          vehicle.getCostPerKm(),
          reps,
          totalVolume,
          reps.size()));
    }
    return ResponseEntity.ok(result);
  }

  @PostMapping
  public ResponseEntity<ReplenishmentDto> createManualRequest(@RequestBody ReplenishmentRequestDto request) {
    if (request == null || request.getSourceDepotId() == null || request.getTargetDepotId() == null || request.getItems() == null || request.getItems().isEmpty()) {
      throw new BadRequestException("sourceDepotId, targetDepotId and items are required");
    }
    List<OrderLine> items = request.getItems().stream().map(item -> {
      double unitVolume = productCatalogService.findById(item.getProductId())
          .orElseThrow(() -> new BadRequestException("Unknown product: " + item.getProductId()))
          .getUnitVolume();
      return new OrderLine(item.getProductId(), item.getQuantity(), unitVolume);
    }).toList();
    for (OrderLine line : items) {
      int available = inventoryService.getAvailableStock(request.getSourceDepotId(), line.getProductId());
      if (available < line.getQuantity()) {
        throw new BadRequestException(String.format(
            "Yetersiz stok: %s deposunda %s urununden %d mevcut, %d talep edildi.",
            request.getSourceDepotId(),
            line.getProductId(),
            available,
            line.getQuantity()));
      }
    }
    double totalVolume = items.stream().mapToDouble(OrderLine::getLineVolume).sum();
    double maxCapacity = simulationEngine.getVehicles().stream()
        .mapToDouble(vehicle -> vehicle.getCapacityVolume())
        .max()
        .orElse(0.0);
    if (maxCapacity > 0.0 && totalVolume > maxCapacity) {
      throw new BadRequestException(String.format(
          "Ikmal hacmi (%.1f m3) en buyuk arac kapasitesini (%.1f m3) asiyor.",
          totalVolume,
          maxCapacity));
    }
    boolean targetMicro = simulationEngine.getDepots().stream()
        .anyMatch(d -> d.getId().equals(request.getTargetDepotId()) && d.isMicro());
    double incomingVolume = replenishmentService.getIncomingVolume(request.getTargetDepotId());
    try {
      inventoryService.validateMicroDepotInboundCapacity(
          request.getTargetDepotId(), items, productCatalogService, targetMicro, incomingVolume);
    } catch (IllegalStateException ex) {
      throw new BadRequestException(ex.getMessage());
    }
    ReplenishmentRequest created = replenishmentService.create(
        request.getSourceDepotId(),
        request.getTargetDepotId(),
        items,
        simulationEngine.getCurrentTick());
    created.setManual(true);
    simulationEngine.submitReplenishmentRequest(created);
    return ResponseEntity.ok(DomainToApiMapper.toReplenishmentDto(created));
  }
}
