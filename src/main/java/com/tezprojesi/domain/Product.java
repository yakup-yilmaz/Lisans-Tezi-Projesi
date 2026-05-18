package com.tezprojesi.domain;

import java.util.Objects;
import java.util.Set;

public final class Product {
  private final String id;
  private final String name;
  private final ProductCategory category;
  private final double unitVolume;
  private final double unitWeight;
  private final Set<ProductCapability> requiredCapabilities;
  private final double financialValue;

  public Product(String id, String name, ProductCategory category, double unitVolume, double unitWeight, double financialValue) {
    this(id, name, category, unitVolume, unitWeight, financialValue, Set.of(ProductCapability.STANDARD));
  }

  public Product(String id, String name, ProductCategory category, double unitVolume, double unitWeight, double financialValue, Set<ProductCapability> requiredCapabilities) {
    this.id = Objects.requireNonNull(id, "id");
    this.name = Objects.requireNonNull(name, "name");
    this.category = Objects.requireNonNull(category, "category");
    if (unitVolume <= 0 || unitWeight < 0 || financialValue < 0) {
      throw new IllegalArgumentException("invalid product dimensions or value");
    }
    this.unitVolume = unitVolume;
    this.unitWeight = unitWeight;
    this.financialValue = financialValue;
    this.requiredCapabilities = Set.copyOf(Objects.requireNonNull(requiredCapabilities, "requiredCapabilities"));
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public ProductCategory getCategory() {
    return category;
  }

  public double getUnitVolume() {
    return unitVolume;
  }

  public double getUnitWeight() {
    return unitWeight;
  }

  public Set<ProductCapability> getRequiredCapabilities() {
    return requiredCapabilities;
  }

  public double getFinancialValue() {
    return financialValue;
  }
}
