package com.group6.project.modules.product.service;

import com.group6.project.modules.product.dto.ProductRequest;
import com.group6.project.modules.product.dto.ProductResponse;

import java.util.List;

public interface ProductService {

    List<ProductResponse> getAllProducts(String search, String category);

    ProductResponse getProductById(String id);

    ProductResponse createProduct(ProductRequest request);

    ProductResponse updateProduct(String id, ProductRequest request);

    void deleteProduct(String id);
}
