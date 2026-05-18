package com.tezprojesi.domain;

import java.time.Instant;
import java.util.Objects;

public final class SimulationRun {

  private final String id;
  private final SimulationConfig config;
  private final SimulationMetrics metrics;
  private final Instant startedAt;
  private Instant endedAt;
  private SimulationStatus status;

  public SimulationRun(String id, SimulationConfig config) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.config = Objects.requireNonNull(config, "config must not be null");
    this.metrics = new SimulationMetrics();
    this.startedAt = Instant.now();
    this.status = SimulationStatus.NOT_STARTED;
  }

  public String getId() {
    return id;
  }

  public SimulationConfig getConfig() {
    return config;
  }

  public SimulationMetrics getMetrics() {
    return metrics;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public Instant getEndedAt() {
    return endedAt;
  }

  public SimulationStatus getStatus() {
    return status;
  }

  public void start() {
    if (status != SimulationStatus.NOT_STARTED) {
      throw new IllegalStateException("Simulation can only start from NOT_STARTED");
    }
    this.status = SimulationStatus.RUNNING;
  }

  public void pause() {
    if (status != SimulationStatus.RUNNING) {
      throw new IllegalStateException("Simulation can only pause from RUNNING");
    }
    this.status = SimulationStatus.PAUSED;
  }

  public void complete() {
    if (status != SimulationStatus.RUNNING && status != SimulationStatus.PAUSED) {
      throw new IllegalStateException("Simulation can only complete from RUNNING or PAUSED");
    }
    this.status = SimulationStatus.COMPLETED;
    this.endedAt = Instant.now();
  }

  public void abort() {
    this.status = SimulationStatus.ABORTED;
    this.endedAt = Instant.now();
  }

  @Override
  public String toString() {
    return "SimulationRun{id='" + id + "', config=" + config + ", metrics=" + metrics + ", status=" + status + ", startedAt=" + startedAt + ", endedAt=" + endedAt + "}";
  }
}
