package com.tezprojesi.api.controller;

import com.tezprojesi.api.dto.InventoryRecordDto;
import com.tezprojesi.api.exception.BadRequestException;
import com.tezprojesi.simulation.InventoryService;
import com.tezprojesi.simulation.SimulationEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {
  private final InventoryService inventoryService;
  private final SimulationEngine engine;

  public InventoryController(InventoryService inventoryService, SimulationEngine simulationEngine) {
    this.inventoryService = Objects.requireNonNull(inventoryService);
    this.engine = Objects.requireNonNull(simulationEngine);
  }

  @GetMapping
  public ResponseEntity<List<InventoryRecordDto>> listInventory(@RequestParam(required = false) String depotId) {
    List<InventoryRecordDto> records = new ArrayList<>();
    Map<String, Map<String, Integer>> snapshot = inventoryService.snapshot();
    snapshot.forEach((currentDepotId, items) -> {
      if (depotId != null && !depotId.equals(currentDepotId)) {
        return;
      }
      items.forEach((productId, quantity) -> {
        InventoryRecordDto dto = new InventoryRecordDto();
        dto.setDepotId(currentDepotId);
        dto.setProductId(productId);
        dto.setQuantity(quantity);
        records.add(dto);
      });
    });
    return ResponseEntity.ok(records);
  }

  @PostMapping
  public ResponseEntity<InventoryRecordDto> setInventory(@RequestBody InventoryRecordDto request) {
    if (request == null || request.getDepotId() == null || request.getProductId() == null || request.getQuantity() < 0) {
      throw new BadRequestException("depotId, productId and a non-negative quantity are required");
    }
    inventoryService.setStock(request.getDepotId(), request.getProductId(), request.getQuantity());
    return ResponseEntity.ok(request);
  }

  @PostMapping("/replenish")
  public ResponseEntity<InventoryRecordDto> replenish(@RequestBody InventoryRecordDto request) {
    if (request == null || request.getDepotId() == null || request.getProductId() == null || request.getQuantity() <= 0) {
      throw new BadRequestException("depotId, productId and a positive quantity are required");
    }
    // ARTIK ANLIK EKLEMEK YERINE SIMULASYON GOREVI OLUSTURUYORUZ
    this.engine.submitManualReplenishmentRequest(request.getDepotId(), request.getProductId(), request.getQuantity());
    
    // Not: Artik aninda eklenmedigi icin mevcut stok dto'da degismeyecek, kullanici arac gelince gorecek
    return ResponseEntity.ok(request);
  }

  @PostMapping("/consume")
  public ResponseEntity<InventoryRecordDto> consume(@RequestBody InventoryRecordDto request) {
    if (request == null || request.getDepotId() == null || request.getProductId() == null || request.getQuantity() <= 0) {
      throw new BadRequestException("depotId, productId and a positive quantity are required");
    }
    inventoryService.consumeManually(request.getDepotId(), request.getProductId(), request.getQuantity());
    request.setQuantity(inventoryService.getTotalStock(request.getDepotId(), request.getProductId()));
    return ResponseEntity.ok(request);
  }
}
