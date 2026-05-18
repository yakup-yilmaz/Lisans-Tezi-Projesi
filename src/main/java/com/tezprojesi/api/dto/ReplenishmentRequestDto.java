package com.tezprojesi.api.dto;

import java.util.List;

public class ReplenishmentRequestDto {
  private String sourceDepotId;
  private String targetDepotId;
  private List<OrderItemDto> items;

  public String getSourceDepotId() { return sourceDepotId; }
  public void setSourceDepotId(String sourceDepotId) { this.sourceDepotId = sourceDepotId; }
  public String getTargetDepotId() { return targetDepotId; }
  public void setTargetDepotId(String targetDepotId) { this.targetDepotId = targetDepotId; }
  public List<OrderItemDto> getItems() { return items; }
  public void setItems(List<OrderItemDto> items) { this.items = items; }
}
