package com.tezprojesi.api.controller;

import com.tezprojesi.api.dto.VehicleDto;
import com.tezprojesi.api.exception.BadRequestException;
import com.tezprojesi.api.exception.NotFoundException;
import com.tezprojesi.api.mapper.DomainToApiMapper;
import com.tezprojesi.api.repository.VehicleRepository;
import com.tezprojesi.domain.ProductCategory;
import com.tezprojesi.domain.Vehicle;
import com.tezprojesi.domain.VehicleCapability;
import com.tezprojesi.simulation.SimulationEngine;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/vehicles")
public class VehicleController {

  private final VehicleRepository vehicleRepository;
  private final SimulationEngine simulationEngine;
  private final List<Vehicle> vehicles;

  public VehicleController(VehicleRepository vehicleRepository, SimulationEngine simulationEngine, List<Vehicle> vehicles) {
    this.vehicleRepository = Objects.requireNonNull(vehicleRepository);
    this.simulationEngine = Objects.requireNonNull(simulationEngine);
    this.vehicles = Objects.requireNonNull(vehicles);
  }

  @GetMapping
  public ResponseEntity<List<VehicleDto>> listVehicles() {
    return ResponseEntity.ok(vehicleRepository.findAll().stream().map(DomainToApiMapper::toVehicleDto).collect(Collectors.toList()));
  }

  @GetMapping("/{vehicleId}")
  public ResponseEntity<VehicleDto> getVehicle(@PathVariable String vehicleId) {
    return vehicleRepository.findById(vehicleId)
        .map(DomainToApiMapper::toVehicleDto)
        .map(ResponseEntity::ok)
        .orElseThrow(() -> new NotFoundException("Vehicle not found"));
  }

  @PostMapping
  public ResponseEntity<VehicleDto> createVehicle(@RequestBody VehicleDto request) {
    if (request == null || request.getId() == null || request.getLocationNodeId() == null) {
      throw new BadRequestException("id and locationNodeId are required");
    }
    Set<ProductCategory> categories = request.getSupportedCategories() == null || request.getSupportedCategories().isEmpty()
        ? Set.of(ProductCategory.GENERAL)
        : request.getSupportedCategories().stream().map(category -> ProductCategory.valueOf(category.toUpperCase())).collect(Collectors.toSet());
    Vehicle vehicle = new Vehicle(
        request.getId(),
        request.getCapacityVolume(),
        request.getLocationNodeId(),
        request.getFuelType() == null ? "DIESEL" : request.getFuelType(),
        request.getCostPerKm(),
        categories,
        request.getCapabilities() == null || request.getCapabilities().isEmpty()
            ? Set.of(VehicleCapability.STANDARD_BOX)
            : request.getCapabilities().stream().map(value -> VehicleCapability.valueOf(value.toUpperCase())).collect(Collectors.toSet()),
        request.getPlateNumber() == null ? request.getId() + "-PLATE" : request.getPlateNumber(),
        request.getDriverName() == null ? "Unassigned" : request.getDriverName(),
        request.getMinimumDepartureLoadFraction(),
        request.getMaxStopCount() <= 0 ? 2 : request.getMaxStopCount(),
        request.getMaxWaitTicks() < 0 ? 180 : request.getMaxWaitTicks(),
        request.getAverageSpeedKmPerHour() <= 0 ? 35.0 : request.getAverageSpeedKmPerHour(),
        request.getLoadingRateVolumePerTick() <= 0 ? 10.0 : request.getLoadingRateVolumePerTick(),
        request.getUnloadingRateVolumePerTick() <= 0 ? 8.0 : request.getUnloadingRateVolumePerTick(),
        request.getBaseLoadTicks() < 0 ? 3 : request.getBaseLoadTicks(),
        request.getBaseUnloadTicks() < 0 ? 2 : request.getBaseUnloadTicks(),
        false);
    simulationEngine.registerVehicle(vehicle);
    if (!simulationEngine.isRunning()) {
      simulationEngine.captureInitialState();
    }
    return ResponseEntity.ok(DomainToApiMapper.toVehicleDto(vehicle));
  }
}
