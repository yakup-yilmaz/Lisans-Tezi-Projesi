package com.tezprojesi.api.dto;

public class ProductDto {
  private String id;
  private String name;
  private String category;
  private double unitVolume;
  private double unitWeight;
  private java.util.Set<String> requiredCapabilities;

  private double financialValue;

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public String getCategory() { return category; }
  public void setCategory(String category) { this.category = category; }
  public double getUnitVolume() { return unitVolume; }
  public void setUnitVolume(double unitVolume) { this.unitVolume = unitVolume; }
  public double getUnitWeight() { return unitWeight; }
  public void setUnitWeight(double unitWeight) { this.unitWeight = unitWeight; }
  public double getFinancialValue() { return financialValue; }
  public void setFinancialValue(double financialValue) { this.financialValue = financialValue; }
  public java.util.Set<String> getRequiredCapabilities() { return requiredCapabilities; }
  public void setRequiredCapabilities(java.util.Set<String> requiredCapabilities) { this.requiredCapabilities = requiredCapabilities; }
}
