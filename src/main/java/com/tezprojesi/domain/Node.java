package com.tezprojesi.domain;

import java.util.Objects;

public final class Node {

  private final String id;
  private final NodeType type;
  private final double x;
  private final double y;

  public Node(String id, NodeType type) {
    this(id, type, 0.0, 0.0);
  }

  public Node(String id, NodeType type, double x, double y) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.type = Objects.requireNonNull(type, "type must not be null");
    this.x = x;
    this.y = y;
  }

  public String getId() {
    return id;
  }

  public NodeType getType() {
    return type;
  }

  public double getX() {
    return x;
  }

  public double getY() {
    return y;
  }

  public boolean isDepot() {
    return type == NodeType.MAIN_DEPOT || type == NodeType.MICRO_DEPOT;
  }

  @Override
  public String toString() {
    return "Node{id='" + id + "', type=" + type + ", x=" + x + ", y=" + y + "}";
  }
}
