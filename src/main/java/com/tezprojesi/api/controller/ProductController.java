package com.tezprojesi.api.controller;

import com.tezprojesi.api.dto.ProductDto;
import com.tezprojesi.api.dto.ProductRequestDto;
import com.tezprojesi.api.exception.BadRequestException;
import com.tezprojesi.api.mapper.DomainToApiMapper;
import com.tezprojesi.domain.Product;
import com.tezprojesi.domain.ProductCapability;
import com.tezprojesi.domain.ProductCategory;
import com.tezprojesi.simulation.ProductCatalogService;
import java.util.List;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
public class ProductController {
  private final ProductCatalogService productCatalogService;

  public ProductController(ProductCatalogService productCatalogService) {
    this.productCatalogService = Objects.requireNonNull(productCatalogService);
  }

  @GetMapping
  public ResponseEntity<List<ProductDto>> listProducts() {
    return ResponseEntity.ok(productCatalogService.findAll().stream().map(DomainToApiMapper::toProductDto).toList());
  }

  @PostMapping
  public ResponseEntity<ProductDto> saveProduct(@RequestBody ProductRequestDto request) {
    if (request == null || request.getId() == null || request.getName() == null || request.getCategory() == null) {
      throw new BadRequestException("id, name and category are required");
    }
    Product product = new Product(
        request.getId(),
        request.getName(),
        ProductCategory.valueOf(request.getCategory().trim().toUpperCase()),
        request.getUnitVolume(),
        request.getUnitWeight(),
        request.getFinancialValue(),
        request.getRequiredCapabilities() == null || request.getRequiredCapabilities().isEmpty()
            ? java.util.Set.of(ProductCapability.STANDARD)
            : request.getRequiredCapabilities().stream().map(value -> ProductCapability.valueOf(value.toUpperCase())).collect(java.util.stream.Collectors.toSet()));
    productCatalogService.save(product);
    return ResponseEntity.ok(DomainToApiMapper.toProductDto(product));
  }
}
