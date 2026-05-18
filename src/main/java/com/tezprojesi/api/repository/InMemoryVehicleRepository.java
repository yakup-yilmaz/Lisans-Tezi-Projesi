package com.tezprojesi.api.repository;

import com.tezprojesi.domain.Vehicle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemoryVehicleRepository implements VehicleRepository {

  private final Map<String, Vehicle> storage = new HashMap<>();

  @Override
  public Vehicle save(Vehicle vehicle) {
    storage.put(vehicle.getId(), vehicle);
    return vehicle;
  }

  @Override
  public Optional<Vehicle> findById(String id) {
    return Optional.ofNullable(storage.get(id));
  }

  @Override
  public List<Vehicle> findAll() {
    return Collections.unmodifiableList(new ArrayList<>(storage.values()));
  }

  @Override
  public void clear() {
    storage.clear();
  }
}
