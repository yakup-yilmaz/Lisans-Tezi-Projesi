package com.tezprojesi.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tezprojesi.domain.OrderLine;
import java.util.List;
import org.junit.jupiter.api.Test;

class InventoryServiceTest {

  @Test
  void reserveAvailable_reducesAvailableWithoutConsumingTotalStock() {
    InventoryService inventoryService = new InventoryService();
    inventoryService.setStock("M1", "P1", 5);

    List<OrderLine> reserved = inventoryService.reserveAvailable("M1", List.of(new OrderLine("P1", 3, 1.0)));

    assertEquals(3, reserved.get(0).getQuantity());
    assertEquals(5, inventoryService.getTotalStock("M1", "P1"));
    assertEquals(3, inventoryService.getReservedStock("M1", "P1"));
    assertEquals(2, inventoryService.getAvailableStock("M1", "P1"));
  }

  @Test
  void consumeReserved_deductsReservationAndStockTogether() {
    InventoryService inventoryService = new InventoryService();
    inventoryService.setStock("M1", "P1", 4);
    inventoryService.reserveAvailable("M1", List.of(new OrderLine("P1", 2, 1.0)));

    inventoryService.consumeReserved("M1", List.of(new OrderLine("P1", 2, 1.0)));

    assertEquals(2, inventoryService.getTotalStock("M1", "P1"));
    assertEquals(0, inventoryService.getReservedStock("M1", "P1"));
    assertEquals(2, inventoryService.getAvailableStock("M1", "P1"));
  }
}
