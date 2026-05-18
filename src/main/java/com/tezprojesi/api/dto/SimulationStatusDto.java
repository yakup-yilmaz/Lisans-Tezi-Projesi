package com.tezprojesi.api.dto;

public class SimulationStatusDto {
  private boolean running;
  private String phase;
  private long currentTick;
  private int maxTicks;
  private int pendingOrders;
  private int activeVehicles;
  private String errorMessage;
  private long failureTick;
  /** Mikro depo otomatik talep eşiği (baseline kesri, örn. 0.30). */
  private double reorderPointFraction;

  public boolean isRunning() { return running; }
  public void setRunning(boolean running) { this.running = running; }
  public String getPhase() { return phase; }
  public void setPhase(String phase) { this.phase = phase; }
  public long getCurrentTick() { return currentTick; }
  public void setCurrentTick(long currentTick) { this.currentTick = currentTick; }
  public int getMaxTicks() { return maxTicks; }
  public void setMaxTicks(int maxTicks) { this.maxTicks = maxTicks; }
  public int getPendingOrders() { return pendingOrders; }
  public void setPendingOrders(int pendingOrders) { this.pendingOrders = pendingOrders; }
  public int getActiveVehicles() { return activeVehicles; }
  public void setActiveVehicles(int activeVehicles) { this.activeVehicles = activeVehicles; }
  public String getErrorMessage() { return errorMessage; }
  public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
  public long getFailureTick() { return failureTick; }
  public void setFailureTick(long failureTick) { this.failureTick = failureTick; }
  public double getReorderPointFraction() { return reorderPointFraction; }
  public void setReorderPointFraction(double reorderPointFraction) { this.reorderPointFraction = reorderPointFraction; }
}
