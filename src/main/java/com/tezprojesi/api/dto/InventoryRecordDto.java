package com.tezprojesi.api.dto;

public class InventoryRecordDto {
  private String depotId;
  private String productId;
  private int quantity;

  public String getDepotId() { return depotId; }
  public void setDepotId(String depotId) { this.depotId = depotId; }
  public String getProductId() { return productId; }
  public void setProductId(String productId) { this.productId = productId; }
  public int getQuantity() { return quantity; }
  public void setQuantity(int quantity) { this.quantity = quantity; }
}
