package com.tezprojesi.domain;

import java.util.Objects;

public final class OrderLine {
  private final String productId;
  private final int quantity;
  private final double unitVolume;

  public OrderLine(String productId, int quantity, double unitVolume) {
    this.productId = Objects.requireNonNull(productId, "productId");
    if (quantity <= 0 || unitVolume <= 0) {
      throw new IllegalArgumentException("invalid order line");
    }
    this.quantity = quantity;
    this.unitVolume = unitVolume;
  }

  public String getProductId() {
    return productId;
  }

  public int getQuantity() {
    return quantity;
  }

  public double getUnitVolume() {
    return unitVolume;
  }

  public double getLineVolume() {
    return quantity * unitVolume;
  }
}
