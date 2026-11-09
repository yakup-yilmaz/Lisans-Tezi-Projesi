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

  @Test
  void advance_throwsWhenTicksNonPositive() {
    assertThrows(BadRequestException.class, () -> controller.advance(0));
    assertThrows(BadRequestException.class, () -> controller.advance(-10));
  }

  @Test
  void summary_excludesReturnTripsAndComputesCostBreakdownAndGasolineCo2Savings() {
    VehicleReportService.TripRecord deliveryTrip = new VehicleReportService.TripRecord(
        "V1", "GASOLINE", 0L, 40L, 100.0, 260.0, 20.0, "REPLENISHMENT",
        0.8, 10L, 4L, 4L, 2, 850.0, 820.0,
        180.0, 50.0, 10.0, 20.0, 5.0, 3.0, 5000.0);
    VehicleReportService.TripRecord returnLeg = new VehicleReportService.TripRecord(
        "V1", "GASOLINE", 40L, 60L, 50.0, 90.0, 0.0, "RETURN",
        0.0, 0L, 0L, 0L, 0, 0.0, -90.0,
        90.0, 0.0, 0.0, 0.0, 0.0, 0.0, 5000.0);

    when(vehicleReportService.getTripRecords()).thenReturn(java.util.List.of(deliveryTrip, returnLeg));

    var response = controller.summary();
    assertEquals(200, response.getStatusCodeValue());
    var dto = response.getBody();

    assertEquals(1L, dto.getTotalTrips(), "RETURN leg must not double-count total trips");
    assertEquals(260.0, dto.getTotalCost(), 0.01);
    assertEquals(180.0, dto.getTotalFuelCost(), 0.01);
    assertEquals(50.0, dto.getTotalTollCost(), 0.01);
    assertEquals(30.0, dto.getTotalLaborCost(), 0.01);
    assertEquals(8.0, dto.getTotalPenaltyCost(), 0.01);
    assertEquals(5000.0, dto.getTotalCargoValue(), 0.01, "Cargo value must not be doubled by RETURN leg");
    assertEquals(1L, dto.getMultiStopTrips());
    assertEquals(0L, dto.getSingleStopTrips(), "0-stop RETURN leg must not be counted as a single-stop delivery");
    assertEquals(10.0, dto.getCo2Saved(), 0.01, "GASOLINE (0.7 kg/km) saves 0.1 kg/km over 0.8 Diesel baseline on 100 km");
  }
}
