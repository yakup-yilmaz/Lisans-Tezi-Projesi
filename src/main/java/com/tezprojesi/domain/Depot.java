package com.tezprojesi.domain;

import java.util.Objects;

public final class Depot {

  private final String id;
  private final DepotType type;
  private final String nodeId;

  public Depot(String id, DepotType type, String nodeId) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.type = Objects.requireNonNull(type, "type must not be null");
    this.nodeId = Objects.requireNonNull(nodeId, "nodeId must not be null");
  }

  public String getId() {
    return id;
  }

  public DepotType getType() {
    return type;
  }

  public String getNodeId() {
    return nodeId;
  }

  public boolean isMain() {
    return type == DepotType.MAIN;
  }

  public boolean isMicro() {
    return type == DepotType.MICRO;
  }

  @Override
  public String toString() {
    return "Depot{id='" + id + "', type=" + type + ", nodeId='" + nodeId + "'}";
  }
}
