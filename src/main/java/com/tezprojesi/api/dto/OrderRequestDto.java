package com.tezprojesi.api.dto;

public class OrderRequestDto {
  private String customerNodeId;
  private int priorityLevel;
  private java.util.List<OrderItemDto> items;

  public String getCustomerNodeId() {
    return customerNodeId;
  }

  public void setCustomerNodeId(String customerNodeId) {
    this.customerNodeId = customerNodeId;
  }

  public int getPriorityLevel() {
    return priorityLevel;
  }

  public void setPriorityLevel(int priorityLevel) {
    this.priorityLevel = priorityLevel;
  }

  public java.util.List<OrderItemDto> getItems() {
    return items;
  }

  public void setItems(java.util.List<OrderItemDto> items) {
    this.items = items;
  }
}
