package com.tezprojesi.api.dto;

public class SimulationEventDto {
  private long id;
  private long tick;
  private String timeLabel;
  private String type;
  private String title;
  private String detail;
  private String entityType;
  private String entityId;

  public long getId() { return id; }
  public void setId(long id) { this.id = id; }
  public long getTick() { return tick; }
  public void setTick(long tick) { this.tick = tick; }
  public String getTimeLabel() { return timeLabel; }
  public void setTimeLabel(String timeLabel) { this.timeLabel = timeLabel; }
  public String getType() { return type; }
  public void setType(String type) { this.type = type; }
  public String getTitle() { return title; }
  public void setTitle(String title) { this.title = title; }
  public String getDetail() { return detail; }
  public void setDetail(String detail) { this.detail = detail; }
  public String getEntityType() { return entityType; }
  public void setEntityType(String entityType) { this.entityType = entityType; }
  public String getEntityId() { return entityId; }
  public void setEntityId(String entityId) { this.entityId = entityId; }
}
