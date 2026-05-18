package com.tezprojesi.api.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tezprojesi.api.dto.KpiDto;
import com.tezprojesi.api.dto.SimulationStatusDto;
import com.tezprojesi.api.exception.BadRequestException;
import com.tezprojesi.simulation.MetricsEventHandler;
import com.tezprojesi.simulation.SimulationEngine;
import com.tezprojesi.simulation.SimulationEventLogService;
import com.tezprojesi.simulation.VehicleReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;

class SimulationControllerTest {

  private SimulationEngine simulationEngine;
  private MetricsEventHandler metricsEventHandler;
  private VehicleReportService vehicleReportService;
  private SimulationEventLogService eventLogService;
  private SimulationController controller;

  @BeforeEach
  void setUp() {
    simulationEngine = Mockito.mock(SimulationEngine.class);
    metricsEventHandler = Mockito.mock(MetricsEventHandler.class);
    vehicleReportService = Mockito.mock(VehicleReportService.class);
    eventLogService = Mockito.mock(SimulationEventLogService.class);
    controller = new SimulationController(simulationEngine, metricsEventHandler, vehicleReportService, eventLogService);
  }

  @Test
  void status_returnsCorrectData() {
    when(simulationEngine.isRunning()).thenReturn(true);
    when(simulationEngine.hasFailure()).thenReturn(false);
    when(simulationEngine.getCurrentTick()).thenReturn(15L);
    when(simulationEngine.getMaxTicks()).thenReturn(100);
    when(simulationEngine.getActiveOrders()).thenReturn(java.util.Map.of("o1", new com.tezprojesi.domain.Order("o1", "Customer1", 1.0)));
    when(simulationEngine.getVehicles()).thenReturn(java.util.List.of(new com.tezprojesi.domain.Vehicle("v1", 10.0, "DepotA")));
    when(simulationEngine.getReorderPointFraction()).thenReturn(0.30);

    ResponseEntity<SimulationStatusDto> result = controller.status();

    assertEquals(200, result.getStatusCodeValue());
    SimulationStatusDto body = result.getBody();
    assertEquals(true, body.isRunning());
    assertEquals(15L, body.getCurrentTick());
    assertEquals(100, body.getMaxTicks());
    assertEquals(1, body.getPendingOrders());
  }

  @Test
  void kpis_returnsMetricData() {
    when(metricsEventHandler.getTotalOrders()).thenReturn(5);
    when(metricsEventHandler.getCompletedOrders()).thenReturn(3);
    when(metricsEventHandler.getTotalDistance()).thenReturn(10.0);
    when(metricsEventHandler.getAverageDeliveryTicks()).thenReturn(20.0);
    when(simulationEngine.getDepotStock()).thenReturn(java.util.Map.of("d1", 80.0));

    ResponseEntity<KpiDto> result = controller.kpis();

    assertEquals(200, result.getStatusCodeValue());
    KpiDto body = result.getBody();
    assertEquals(5, body.getTotalOrders());
    assertEquals(3, body.getCompletedOrders());
    assertEquals(10.0, body.getTotalDistance());
    assertEquals(20.0, body.getAvgDeliveryTime());
  }

  @Test
  void start_callsStartAsync() {
    when(simulationEngine.isRunning()).thenReturn(false);
    when(simulationEngine.hasFailure()).thenReturn(false);
    doNothing().when(simulationEngine).startAsync();

    ResponseEntity<Void> result = controller.start();

    assertEquals(202, result.getStatusCodeValue());
  }

  @Test
  void stop_returnsOkEvenWhenAlreadyStopped() {
    when(simulationEngine.isRunning()).thenReturn(false);

    ResponseEntity<Void> result = controller.stop();

    assertEquals(200, result.getStatusCodeValue());
  }

  @Test
  void stop_callsSimulationStopWhenRunning() {
    when(simulationEngine.isRunning()).thenReturn(true);
    doNothing().when(simulationEngine).stop();

    ResponseEntity<Void> result = controller.stop();

    assertEquals(200, result.getStatusCodeValue());
    verify(simulationEngine).stop();
  }

  @Test
  void start_throwsWhenSimulationIsInErrorState() {
    when(simulationEngine.hasFailure()).thenReturn(true);

    assertThrows(BadRequestException.class, () -> controller.start());
  }

  @Test
  void advance_throwsWhenSimulationIsInErrorState() {
    when(simulationEngine.isRunning()).thenReturn(false);
    when(simulationEngine.hasFailure()).thenReturn(true);

    assertThrows(BadRequestException.class, () -> controller.advance(60));
  }

  @Test
  void dispatchReady_callsEngineApproval() {
    doNothing().when(simulationEngine).approveReadyDispatch("V1");

    ResponseEntity<Void> result = controller.dispatchReady("V1");

    assertEquals(200, result.getStatusCodeValue());
    verify(simulationEngine).approveReadyDispatch("V1");
  }
}
