package com.tezprojesi.api.controller;

import com.tezprojesi.api.dto.OrderResponseDto;
import com.tezprojesi.api.exception.NotFoundException;
import com.tezprojesi.api.mapper.DomainToApiMapper;
import com.tezprojesi.api.repository.OrderRepository;
import com.tezprojesi.domain.Depot;
import com.tezprojesi.domain.Order;
import com.tezprojesi.domain.OrderLine;
import com.tezprojesi.routing.CityGraph;
import com.tezprojesi.simulation.SimulationEngine;
import com.tezprojesi.simulation.ProductCatalogService;
import java.util.List;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

  private final OrderRepository orderRepository;
  private final ProductCatalogService productCatalogService;
  private final SimulationEngine simulationEngine;

  public OrderController(OrderRepository orderRepository, ProductCatalogService productCatalogService, SimulationEngine simulationEngine) {
    this.orderRepository = Objects.requireNonNull(orderRepository);
    this.productCatalogService = Objects.requireNonNull(productCatalogService);
    this.simulationEngine = Objects.requireNonNull(simulationEngine);
  }



  @GetMapping
  public ResponseEntity<List<OrderResponseDto>> listOrders() {
    return ResponseEntity.ok(orderRepository.findAll().stream().map(DomainToApiMapper::toOrderResponse).toList());
  }

  @GetMapping("/{orderId}")
  public ResponseEntity<OrderResponseDto> getOrder(@PathVariable String orderId) {
    Order order = orderRepository.findById(orderId).orElseThrow(() -> new NotFoundException("Order not found"));
    return ResponseEntity.ok(DomainToApiMapper.toOrderResponse(order));
  }


}
