package com.tezprojesi.api.dto;

public class OrderItemDto {
  private String productId;
  private int quantity;
  private double unitVolume;

  public String getProductId() { return productId; }
  public void setProductId(String productId) { this.productId = productId; }
  public int getQuantity() { return quantity; }
  public void setQuantity(int quantity) { this.quantity = quantity; }
  public double getUnitVolume() { return unitVolume; }
  public void setUnitVolume(double unitVolume) { this.unitVolume = unitVolume; }
}
