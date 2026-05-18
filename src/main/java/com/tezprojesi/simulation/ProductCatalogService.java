package com.tezprojesi.simulation;

import com.tezprojesi.domain.Product;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ProductCatalogService {
  private final Map<String, Product> products = new HashMap<>();

  public Product save(Product product) {
    Product safeProduct = java.util.Objects.requireNonNull(product, "product");
    products.put(safeProduct.getId(), safeProduct);
    return safeProduct;
  }

  public Optional<Product> findById(String productId) {
    return Optional.ofNullable(products.get(productId));
  }

  public List<Product> findAll() {
    return List.copyOf(new ArrayList<>(products.values()));
  }

  public void clear() {
    products.clear();
  }
}
