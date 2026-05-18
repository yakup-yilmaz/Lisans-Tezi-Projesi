package com.tezprojesi.api.controller;

import com.tezprojesi.api.dto.EdgeDto;
import com.tezprojesi.api.dto.GraphEdgeUpsertRequest;
import com.tezprojesi.api.dto.GraphNodeUpsertRequest;
import com.tezprojesi.api.dto.NodeDto;
import com.tezprojesi.api.dto.RouteDto;
import com.tezprojesi.api.dto.TollUpdateRequest;
import com.tezprojesi.api.dto.TrafficUpdateRequest;
import com.tezprojesi.api.exception.BadRequestException;
import com.tezprojesi.api.mapper.DomainToApiMapper;
import com.tezprojesi.domain.Edge;
import com.tezprojesi.domain.Node;
import com.tezprojesi.domain.NodeType;
import com.tezprojesi.routing.CityGraph;
import com.tezprojesi.routing.RoutingService;
import com.tezprojesi.simulation.TrafficManager;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/graph")
public class GraphController {

  private final CityGraph cityGraph;
  private final RoutingService routingService;
  private final TrafficManager trafficManager;
  
  private static final double MAX_DISTANCE_KM = 10000.0;
  private static final double MIN_DISTANCE_KM = 0.001;
  private static final double MAX_TRAFFIC_FACTOR = 10.0;
  private static final double MIN_TRAFFIC_FACTOR = 0.01;
  private static final double MAX_TOLL_COST = 1000.0;

  public GraphController(CityGraph cityGraph, RoutingService routingService, TrafficManager trafficManager) {
    this.cityGraph = Objects.requireNonNull(cityGraph);
    this.routingService = Objects.requireNonNull(routingService);
    this.trafficManager = Objects.requireNonNull(trafficManager);
  }

  @GetMapping("/nodes")
  public ResponseEntity<List<NodeDto>> listNodes() {
    List<NodeDto> nodes = cityGraph.getAllNodes().stream()
        .map(DomainToApiMapper::toNodeDto)
        .collect(Collectors.toList());
    return ResponseEntity.ok(nodes);
  }

  @GetMapping("/edges")
  public ResponseEntity<List<EdgeDto>> listEdges() {
    List<EdgeDto> edges = cityGraph.getEdges().stream()
        .map(this::toEdgeDto)
        .collect(Collectors.toList());
    return ResponseEntity.ok(edges);
  }

  @GetMapping("/nodes/{nodeId}/neighbors")
  public ResponseEntity<List<EdgeDto>> getNeighbors(@PathVariable String nodeId) {
    List<EdgeDto> inbound = cityGraph.getNeighbors(nodeId).stream()
        .map(this::toEdgeDto)
        .collect(Collectors.toList());
    return ResponseEntity.ok(inbound);
  }

  @PostMapping("/nodes")
  public ResponseEntity<NodeDto> upsertNode(@RequestBody GraphNodeUpsertRequest request) {
    if (request == null || isBlank(request.getId()) || isBlank(request.getType())) {
      throw new BadRequestException("Node id and type are required");
    }
    NodeType type;
    try {
      type = NodeType.valueOf(request.getType().trim().toUpperCase());
    } catch (IllegalArgumentException ex) {
      throw new BadRequestException("Unknown node type: " + request.getType());
    }

    Node node = new Node(request.getId().trim(), type, request.getX(), request.getY());
    cityGraph.upsertNode(node);
    return ResponseEntity.ok(DomainToApiMapper.toNodeDto(node));
  }

  @PostMapping("/edges")
  public ResponseEntity<EdgeDto> upsertEdge(@RequestBody GraphEdgeUpsertRequest request) {
    if (request == null || isBlank(request.getFromNodeId()) || isBlank(request.getToNodeId())) {
      throw new BadRequestException("fromNodeId and toNodeId are required");
    }
    validateDistanceKm(request.getDistanceKm());
    validateTrafficFactor(request.getTrafficFactor());
    validateTollCost(request.getTollCost());
    ensureNodeExists(request.getFromNodeId());
    ensureNodeExists(request.getToNodeId());

    cityGraph.setEdge(
        request.getFromNodeId(),
        request.getToNodeId(),
        request.getDistanceKm(),
        request.getTrafficFactor(),
        request.getTollCost());

    if (request.isBidirectional()) {
      cityGraph.setEdge(
          request.getToNodeId(),
          request.getFromNodeId(),
          request.getDistanceKm(),
          request.getTrafficFactor(),
          request.getTollCost());
    }

    return ResponseEntity.ok(toEdgeDto(cityGraph.getEdge(request.getFromNodeId(), request.getToNodeId())));
  }

  @PutMapping("/edges/traffic")
  public ResponseEntity<EdgeDto> updateTraffic(@RequestBody TrafficUpdateRequest request) {
    if (request == null || isBlank(request.getFromNodeId()) || isBlank(request.getToNodeId())) {
      throw new BadRequestException("fromNodeId and toNodeId are required");
    }
    validateTrafficFactor(request.getTrafficFactor());
    ensureEdgeExists(request.getFromNodeId(), request.getToNodeId());
    applyTrafficUpdate(request.getFromNodeId(), request.getToNodeId(), request.getTrafficFactor(), request.isLocked());
    if (request.isBidirectional()) {
      ensureEdgeExists(request.getToNodeId(), request.getFromNodeId());
      applyTrafficUpdate(request.getToNodeId(), request.getFromNodeId(), request.getTrafficFactor(), request.isLocked());
    }
    return ResponseEntity.ok(toEdgeDto(cityGraph.getEdge(request.getFromNodeId(), request.getToNodeId())));
  }

  @PutMapping("/edges/toll")
  public ResponseEntity<EdgeDto> updateToll(@RequestBody TollUpdateRequest request) {
    if (request == null || isBlank(request.getFromNodeId()) || isBlank(request.getToNodeId())) {
      throw new BadRequestException("fromNodeId and toNodeId are required");
    }
    validateTollCost(request.getTollCost());
    ensureEdgeExists(request.getFromNodeId(), request.getToNodeId());
    cityGraph.updateTollCost(request.getFromNodeId(), request.getToNodeId(), request.getTollCost());
    if (request.isBidirectional()) {
      ensureEdgeExists(request.getToNodeId(), request.getFromNodeId());
      cityGraph.updateTollCost(request.getToNodeId(), request.getFromNodeId(), request.getTollCost());
    }
    return ResponseEntity.ok(toEdgeDto(cityGraph.getEdge(request.getFromNodeId(), request.getToNodeId())));
  }

  @GetMapping("/route")
  public ResponseEntity<RouteDto> computeRoute(
      @RequestParam String sourceNodeId,
      @RequestParam String targetNodeId,
      @RequestParam(defaultValue = "1.0") double tollPenalty) {
    if (tollPenalty < 0) {
      throw new BadRequestException("tollPenalty must be non-negative");
    }
    ensureNodeExists(sourceNodeId);
    ensureNodeExists(targetNodeId);

    List<String> route = routingService.computeShortestPath(cityGraph, sourceNodeId, targetNodeId, tollPenalty);
    if (route.isEmpty()) {
      throw new BadRequestException("No route exists between the selected nodes");
    }

    RouteDto dto = new RouteDto();
    dto.setSourceNodeId(sourceNodeId);
    dto.setTargetNodeId(targetNodeId);
    dto.setNodeIds(route);
    dto.setTollPenalty(tollPenalty);
    dto.setTotalCost(routingService.computePathCost(cityGraph, route, tollPenalty));
    dto.setTotalDistanceKm(computeTotalDistance(route));
    return ResponseEntity.ok(dto);
  }

  private void applyTrafficUpdate(String fromNodeId, String toNodeId, double trafficFactor, boolean locked) {
    if (locked) {
      trafficManager.setFixedTrafficFactor(cityGraph, fromNodeId, toNodeId, trafficFactor);
      return;
    }
    trafficManager.clearFixedTrafficFactor(fromNodeId, toNodeId);
    cityGraph.updateTrafficFactor(fromNodeId, toNodeId, trafficFactor);
  }

  private double computeTotalDistance(List<String> route) {
    double total = 0.0;
    for (int i = 0; i < route.size() - 1; i++) {
      Edge edge = cityGraph.getEdge(route.get(i), route.get(i + 1));
      if (edge == null) {
        throw new BadRequestException("Route contains a missing edge");
      }
      total += edge.getDistanceKm();
    }
    return total;
  }

  private void ensureNodeExists(String nodeId) {
    if (!cityGraph.hasNode(nodeId)) {
      throw new BadRequestException("Node not found: " + nodeId);
    }
  }

  private void ensureEdgeExists(String fromNodeId, String toNodeId) {
    if (!cityGraph.hasEdge(fromNodeId, toNodeId)) {
      throw new BadRequestException("Edge not found: " + fromNodeId + " -> " + toNodeId);
    }
  }

  private boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }

  private void validateDistanceKm(double distanceKm) {
    if (distanceKm < MIN_DISTANCE_KM || distanceKm > MAX_DISTANCE_KM) {
      throw new BadRequestException(
          String.format("distanceKm must be between %.3f and %.1f km, got %.3f", 
              MIN_DISTANCE_KM, MAX_DISTANCE_KM, distanceKm));
    }
  }

  private void validateTrafficFactor(double trafficFactor) {
    if (trafficFactor < MIN_TRAFFIC_FACTOR || trafficFactor > MAX_TRAFFIC_FACTOR) {
      throw new BadRequestException(
          String.format("trafficFactor must be between %.2f and %.1f, got %.2f", 
              MIN_TRAFFIC_FACTOR, MAX_TRAFFIC_FACTOR, trafficFactor));
    }
  }

  private void validateTollCost(double tollCost) {
    if (tollCost < 0 || tollCost > MAX_TOLL_COST) {
      throw new BadRequestException(
          String.format("tollCost must be between 0 and %.1f, got %.2f", 
              MAX_TOLL_COST, tollCost));
    }
  }

  private EdgeDto toEdgeDto(Edge edge) {
    EdgeDto dto = DomainToApiMapper.toEdgeDto(edge);
    dto.setTrafficLocked(trafficManager.hasFixedTrafficFactor(edge.getFromNodeId(), edge.getToNodeId()));
    return dto;
  }
}
