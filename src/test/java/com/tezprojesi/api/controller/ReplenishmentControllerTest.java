package com.tezprojesi.api.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tezprojesi.api.dto.OrderItemDto;
import com.tezprojesi.api.dto.ReplenishmentDto;
import com.tezprojesi.api.dto.ReplenishmentRequestDto;
import com.tezprojesi.domain.Depot;
import com.tezprojesi.domain.DepotType;
import com.tezprojesi.domain.Product;
import com.tezprojesi.domain.ProductCategory;
import com.tezprojesi.domain.ReplenishmentRequest;
import com.tezprojesi.domain.Vehicle;
import com.tezprojesi.simulation.InventoryService;
import com.tezprojesi.simulation.ProductCatalogService;
import com.tezprojesi.simulation.ReplenishmentService;
import com.tezprojesi.simulation.SimulationEngine;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;

class ReplenishmentControllerTest {

  private ReplenishmentService replenishmentService;
  private ProductCatalogService productCatalogService;
  private InventoryService inventoryService;
  private SimulationEngine simulationEngine;
  private ReplenishmentController controller;

  @BeforeEach
  void setUp() {
    replenishmentService = Mockito.mock(ReplenishmentService.class);
    productCatalogService = Mockito.mock(ProductCatalogService.class);
    inventoryService = Mockito.mock(InventoryService.class);
    simulationEngine = Mockito.mock(SimulationEngine.class);
    controller = new ReplenishmentController(replenishmentService, productCatalogService, inventoryService, simulationEngine);
  }

  @Test
  void createManualRequest_usesCurrentSimulationTick() {
    when(simulationEngine.getCurrentTick()).thenReturn(42L);
    when(simulationEngine.getVehicles()).thenReturn(List.of(new Vehicle("V1", 10.0, "MAIN")));
    when(simulationEngine.getDepots()).thenReturn(List.of(new Depot("M1", DepotType.MICRO, "M1")));
    when(productCatalogService.findById("P1")).thenReturn(Optional.of(new Product("P1", "Water", ProductCategory.FOOD, 1.5, 1.0, 10.0)));
    when(inventoryService.getAvailableStock("MAIN", "P1")).thenReturn(10);
    ReplenishmentRequest created = new ReplenishmentRequest("repl-1", "MAIN", "M1", List.of(new com.tezprojesi.domain.OrderLine("P1", 3, 1.5)), 42L);
    when(replenishmentService.create(any(), any(), any(), Mockito.eq(42L))).thenReturn(created);

    ReplenishmentRequestDto request = new ReplenishmentRequestDto();
    request.setSourceDepotId("MAIN");
    request.setTargetDepotId("M1");
    OrderItemDto item = new OrderItemDto();
    item.setProductId("P1");
    item.setQuantity(3);
    request.setItems(List.of(item));

    ResponseEntity<ReplenishmentDto> response = controller.createManualRequest(request);

    assertEquals(200, response.getStatusCodeValue());
    assertEquals(42L, response.getBody().getCreatedTick());
    verify(replenishmentService).create(any(), any(), any(), Mockito.eq(42L));
    verify(simulationEngine).submitReplenishmentRequest(created);
  }
}
