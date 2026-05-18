package com.tezprojesi.api.mapper;

import com.tezprojesi.api.dto.EdgeDto;
import com.tezprojesi.api.dto.OrderItemDto;
import com.tezprojesi.api.dto.NodeDto;
import com.tezprojesi.api.dto.OrderResponseDto;
import com.tezprojesi.api.dto.ProductDto;
import com.tezprojesi.api.dto.ReplenishmentDto;
import com.tezprojesi.api.dto.VehicleDto;
import com.tezprojesi.domain.OrderLine;
import com.tezprojesi.domain.Order;
import com.tezprojesi.domain.Product;
import com.tezprojesi.domain.ReplenishmentRequest;
import com.tezprojesi.domain.Vehicle;
import com.tezprojesi.domain.Edge;
import com.tezprojesi.domain.Node;

public final class DomainToApiMapper {

  private DomainToApiMapper() {}

  public static OrderResponseDto toOrderResponse(Order order) {
    if (order == null) {
      return null;
    }
    OrderResponseDto dto = new OrderResponseDto();
    dto.setId(order.getId());
    dto.setCustomerNodeId(order.getCustomerNodeId());
    dto.setMicroDepotNodeId(order.getMicroDepotNodeId());
    dto.setDemandVolume(order.getDemandVolume());
    dto.setStatus(order.getStatus().name());
    dto.setAssignedVehicleId(order.getAssignedVehicleId());
    dto.setCreatedAt(order.getCreatedAt());
    dto.setItems(order.getItems().stream().map(DomainToApiMapper::toOrderItemDto).toList());
    return dto;
  }

  public static VehicleDto toVehicleDto(Vehicle vehicle) {
    if (vehicle == null) {
      return null;
    }
    VehicleDto dto = new VehicleDto();
    dto.setId(vehicle.getId());
    dto.setCapacityVolume(vehicle.getCapacityVolume());
    dto.setCurrentLoad(vehicle.getCurrentLoad());
    dto.setLocationNodeId(vehicle.getLocationNodeId());
    dto.setStatus(vehicle.getStatus().name());
    dto.setFuelType(vehicle.getFuelType());
    dto.setCostPerKm(vehicle.getCostPerKm());
    dto.setSupportedCategories(vehicle.getSupportedCategories().stream().map(Enum::name).collect(java.util.stream.Collectors.toSet()));
    dto.setCapabilities(vehicle.getCapabilities().stream().map(Enum::name).collect(java.util.stream.Collectors.toSet()));
    dto.setPlateNumber(vehicle.getPlateNumber());
    dto.setDriverName(vehicle.getDriverName());
    dto.setMinimumDepartureLoadFraction(vehicle.getMinimumDepartureLoadFraction());
    dto.setMaxStopCount(vehicle.getMaxStopCount());
    dto.setMaxWaitTicks(vehicle.getMaxWaitTicks());
    dto.setLoadFraction(vehicle.getLoadFraction());
    dto.setAverageSpeedKmPerHour(vehicle.getAverageSpeedKmPerHour());
    dto.setLoadingRateVolumePerTick(vehicle.getLoadingRateVolumePerTick());
    dto.setUnloadingRateVolumePerTick(vehicle.getUnloadingRateVolumePerTick());
    dto.setBaseLoadTicks(vehicle.getBaseLoadTicks());
    dto.setBaseUnloadTicks(vehicle.getBaseUnloadTicks());
    dto.setDedicatedManual(vehicle.isDedicatedManual());
    return dto;
  }

  public static NodeDto toNodeDto(Node node) {
    NodeDto dto = new NodeDto();
    dto.setId(node.getId());
    dto.setType(node.getType().name());
    dto.setX(node.getX());
    dto.setY(node.getY());
    return dto;
  }

  public static EdgeDto toEdgeDto(Edge edge) {
    EdgeDto dto = new EdgeDto();
    dto.setFromNodeId(edge.getFromNodeId());
    dto.setToNodeId(edge.getToNodeId());
    dto.setDistanceKm(edge.getDistanceKm());
    dto.setTrafficFactor(edge.getTrafficFactor());
    dto.setTollCost(edge.getTollCost());
    return dto;
  }

  public static OrderItemDto toOrderItemDto(OrderLine line) {
    OrderItemDto dto = new OrderItemDto();
    dto.setProductId(line.getProductId());
    dto.setQuantity(line.getQuantity());
    dto.setUnitVolume(line.getUnitVolume());
    return dto;
  }

  public static ProductDto toProductDto(Product product) {
    ProductDto dto = new ProductDto();
    dto.setId(product.getId());
    dto.setName(product.getName());
    dto.setCategory(product.getCategory().name());
    dto.setUnitVolume(product.getUnitVolume());
    dto.setUnitWeight(product.getUnitWeight());
    dto.setFinancialValue(product.getFinancialValue());
    dto.setRequiredCapabilities(product.getRequiredCapabilities().stream().map(Enum::name).collect(java.util.stream.Collectors.toSet()));
    return dto;
  }

  public static ReplenishmentDto toReplenishmentDto(ReplenishmentRequest request) {
    ReplenishmentDto dto = new ReplenishmentDto();
    dto.setId(request.getId());
    dto.setSourceDepotId(request.getSourceDepotId());
    dto.setTargetDepotId(request.getTargetDepotId());
    dto.setStatus(request.getStatus().name());
    dto.setAssignedVehicleId(request.getAssignedVehicleId());
    dto.setTotalVolume(request.getTotalVolume());
    dto.setCreatedAt(request.getCreatedAt());
    dto.setCreatedTick(request.getCreatedTick());
    dto.setDecisionReason(request.getDecisionReason());
    dto.setComparisonSummary(request.getComparisonSummary());
    dto.setRouteSummary(request.getRouteSummary());
    dto.setCargoSummary(request.getCargoSummary());
    dto.setPlanCost(request.getPlanCost());
    dto.setPlanBenefit(request.getPlanBenefit());
    dto.setNetScore(request.getNetScore());
    dto.setTotalFinancialValue(request.getTotalFinancialValue());
    dto.setAwaitingApproval(request.getStatus().name().equals("CREATED") && request.getAssignedVehicleId() != null);
    dto.setItems(request.getItems().stream().map(DomainToApiMapper::toOrderItemDto).toList());
    return dto;
  }

}
