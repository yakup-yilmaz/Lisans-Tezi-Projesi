package com.tezprojesi.simulation;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

public final class SimulationEventLogService {

  private final CopyOnWriteArrayList<SimulationEventRecord> events = new CopyOnWriteArrayList<>();
  private final AtomicLong sequence = new AtomicLong();

  public void record(long tick, String type, String title, String detail, String entityType, String entityId) {
    events.add(new SimulationEventRecord(
        sequence.incrementAndGet(),
        Math.max(0L, tick),
        type,
        title,
        detail,
        entityType,
        entityId));
  }

  public List<SimulationEventRecord> findAll() {
    return List.copyOf(new ArrayList<>(events));
  }

  public List<SimulationEventRecord> latest(int limit) {
    int safeLimit = Math.max(1, limit);
    List<SimulationEventRecord> snapshot = new ArrayList<>(events);
    int fromIndex = Math.max(0, snapshot.size() - safeLimit);
    return List.copyOf(snapshot.subList(fromIndex, snapshot.size()));
  }

  public void clear() {
    events.clear();
    sequence.set(0L);
  }

  public record SimulationEventRecord(
      long id,
      long tick,
      String type,
      String title,
      String detail,
      String entityType,
      String entityId) {}
}
