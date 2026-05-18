package com.tezprojesi.api.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tezprojesi.api.dto.VehicleDto;
import com.tezprojesi.api.repository.VehicleRepository;
import com.tezprojesi.simulation.SimulationEngine;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;

class VehicleControllerTest {

  private VehicleRepository vehicleRepository;
  private SimulationEngine simulationEngine;
  private VehicleController controller;

  @BeforeEach
  void setUp() {
    vehicleRepository = Mockito.mock(VehicleRepository.class);
    simulationEngine = Mockito.mock(SimulationEngine.class);
    controller = new VehicleController(vehicleRepository, simulationEngine, List.of());
  }

  @Test
  void createVehicle_preservesZeroMaxWaitTicks() {
    when(simulationEngine.isRunning()).thenReturn(false);

    VehicleDto request = new VehicleDto();
    request.setId("V1");
    request.setCapacityVolume(10.0);
    request.setLocationNodeId("MAIN");
    request.setFuelType("DIESEL");
    request.setCostPerKm(2.0);
    request.setSupportedCategories(Set.of("FOOD"));
    request.setCapabilities(Set.of("STANDARD_BOX"));
    request.setPlateNumber("34ABC001");
    request.setDriverName("Test Driver");
    request.setMinimumDepartureLoadFraction(0.4);
    request.setMaxStopCount(2);
    request.setMaxWaitTicks(0L);
    request.setAverageSpeedKmPerHour(35.0);
    request.setLoadingRateVolumePerTick(10.0);
    request.setUnloadingRateVolumePerTick(8.0);
    request.setBaseLoadTicks(3L);
    request.setBaseUnloadTicks(2L);

    ResponseEntity<VehicleDto> response = controller.createVehicle(request);

    assertEquals(200, response.getStatusCodeValue());
    assertEquals(0L, response.getBody().getMaxWaitTicks());
    verify(simulationEngine).registerVehicle(Mockito.any());
    verify(simulationEngine).captureInitialState();
  }
}
