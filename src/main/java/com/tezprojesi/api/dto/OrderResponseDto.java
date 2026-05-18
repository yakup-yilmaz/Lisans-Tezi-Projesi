package com.tezprojesi.api.dto;

import java.time.Instant;

public class OrderResponseDto {
  private String id;
  private String customerNodeId;
  private String microDepotNodeId;
  private double demandVolume;
  private String status;
  private String assignedVehicleId;
  private Instant createdAt;
  private java.util.List<OrderItemDto> items;

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getCustomerNodeId() { return customerNodeId; }
  public void setCustomerNodeId(String customerNodeId) { this.customerNodeId = customerNodeId; }
  public String getMicroDepotNodeId() { return microDepotNodeId; }
  public void setMicroDepotNodeId(String microDepotNodeId) { this.microDepotNodeId = microDepotNodeId; }
  public double getDemandVolume() { return demandVolume; }
  public void setDemandVolume(double demandVolume) { this.demandVolume = demandVolume; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public String getAssignedVehicleId() { return assignedVehicleId; }
  public void setAssignedVehicleId(String assignedVehicleId) { this.assignedVehicleId = assignedVehicleId; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public java.util.List<OrderItemDto> getItems() { return items; }
  public void setItems(java.util.List<OrderItemDto> items) { this.items = items; }
}
