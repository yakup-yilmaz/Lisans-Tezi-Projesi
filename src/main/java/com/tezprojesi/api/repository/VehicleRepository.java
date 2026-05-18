package com.tezprojesi.api.repository;

import com.tezprojesi.domain.Vehicle;
import java.util.List;
import java.util.Optional;

public interface VehicleRepository {
  Vehicle save(Vehicle vehicle);
  Optional<Vehicle> findById(String id);
  List<Vehicle> findAll();
  void clear();
}
