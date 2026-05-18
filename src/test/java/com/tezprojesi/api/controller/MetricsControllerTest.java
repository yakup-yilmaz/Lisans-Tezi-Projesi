package com.tezprojesi.api.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.tezprojesi.api.dto.MetricsReportDto;
import com.tezprojesi.simulation.MetricsEventHandler;
import com.tezprojesi.simulation.SimulationEngine;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;

class MetricsControllerTest {

  private MetricsEventHandler metricsEventHandler;
  private SimulationEngine simulationEngine;
  private MetricsController controller;

  @BeforeEach
  void setUp() {
    metricsEventHandler = Mockito.mock(MetricsEventHandler.class);
    simulationEngine = Mockito.mock(SimulationEngine.class);
    controller = new MetricsController(metricsEventHandler, simulationEngine);
  }

  @Test
  void getDistancePerVehicle_returnsExpected() {
    when(metricsEventHandler.getDistancePerVehicle()).thenReturn(Map.of("v1", 42.0));

    ResponseEntity<Map<String, Double>> response = controller.getDistancePerVehicle();

    assertEquals(200, response.getStatusCodeValue());
    assertEquals(1, response.getBody().size());
    assertEquals(42.0, response.getBody().get("v1"));
  }

  @Test
  void getUtilizationPerVehicle_returnsExpected() {
    when(metricsEventHandler.getUtilizationPerVehicle()).thenReturn(Map.of("v1", 120L));

    ResponseEntity<Map<String, Long>> response = controller.getUtilizationPerVehicle();

    assertEquals(200, response.getStatusCodeValue());
    assertEquals(120L, response.getBody().get("v1"));
  }

  @Test
  void getReport_returnsSummary() {
    when(simulationEngine.getCurrentTick()).thenReturn(13L);
    when(metricsEventHandler.getTotalOrders()).thenReturn(7);
    when(metricsEventHandler.getCompletedOrders()).thenReturn(6);
    when(metricsEventHandler.getTotalDistance()).thenReturn(99.5);
    when(metricsEventHandler.getAverageDeliveryTicks()).thenReturn(5.5);
    when(metricsEventHandler.getDelayedOrders()).thenReturn(2);
    when(metricsEventHandler.getDistancePerVehicle()).thenReturn(Map.of("v1", 99.5));
    when(metricsEventHandler.getUtilizationPerVehicle()).thenReturn(Map.of("v1", 66L));
    when(metricsEventHandler.getDepotDispatchCount()).thenReturn(Map.of("d1", 3));
    when(metricsEventHandler.getDepotStockChange()).thenReturn(Map.of("d1", -20L));

    ResponseEntity<MetricsReportDto> response = controller.getMetricsReport(1, 0);

    assertEquals(200, response.getStatusCodeValue());
    MetricsReportDto report = response.getBody();
    assertEquals(1L, report.getFromTick());
    assertEquals(13L, report.getToTick());
    assertEquals(7L, report.getTotalOrders());
    assertEquals(6L, report.getCompletedOrders());
    assertEquals(99.5, report.getTotalDistance());
    assertEquals(2L, report.getDelayedOrders());
    assertEquals(1, report.getDepotDispatchCount().size());
  }
}
