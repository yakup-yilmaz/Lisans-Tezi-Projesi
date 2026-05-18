package com.tezprojesi.api.dto;

import java.util.List;

/**
 * Combines multiple simulation data in a single response to reduce API calls.
 * Reduces 5 separate calls to 1 call during polling.
 *
 * API calls reduced: /status + /vehicle-assignments + /timeline + /summary + /dispatch-readiness = 1 call
 */
public class SimulationDashboardDto {
  private SimulationStatusDto status;
  private List<VehicleAssignmentDto> vehicleAssignments;
  private List<SimulationEventDto> timeline;
  private SimulationSummaryDto summary;
  private List<VehicleDispatchReadinessDto> dispatchReadiness;

  public SimulationDashboardDto() {}

  public SimulationDashboardDto(
      SimulationStatusDto status,
      List<VehicleAssignmentDto> vehicleAssignments,
      List<SimulationEventDto> timeline,
      SimulationSummaryDto summary,
      List<VehicleDispatchReadinessDto> dispatchReadiness) {
    this.status = status;
    this.vehicleAssignments = vehicleAssignments;
    this.timeline = timeline;
    this.summary = summary;
    this.dispatchReadiness = dispatchReadiness;
  }

  public SimulationStatusDto getStatus() {
    return status;
  }

  public void setStatus(SimulationStatusDto status) {
    this.status = status;
  }

  public List<VehicleAssignmentDto> getVehicleAssignments() {
    return vehicleAssignments;
  }

  public void setVehicleAssignments(List<VehicleAssignmentDto> vehicleAssignments) {
    this.vehicleAssignments = vehicleAssignments;
  }

  public List<SimulationEventDto> getTimeline() {
    return timeline;
  }

  public void setTimeline(List<SimulationEventDto> timeline) {
    this.timeline = timeline;
  }

  public SimulationSummaryDto getSummary() {
    return summary;
  }

  public void setSummary(SimulationSummaryDto summary) {
    this.summary = summary;
  }

  public List<VehicleDispatchReadinessDto> getDispatchReadiness() {
    return dispatchReadiness;
  }

  public void setDispatchReadiness(List<VehicleDispatchReadinessDto> dispatchReadiness) {
    this.dispatchReadiness = dispatchReadiness;
  }
}
