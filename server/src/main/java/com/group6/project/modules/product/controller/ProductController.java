package com.group6.project.modules.product.controller;

import com.group6.project.common.response.ApiResponse;
import com.group6.project.modules.product.dto.ProductRequest;
import com.group6.project.modules.product.dto.ProductResponse;
import com.group6.project.modules.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Product Management REST API endpoints")
public class ProductController {

    private final ProductService productService;

    @GetMapping
    @Operation(summary = "Get all products", description = "Retrieve list of products with optional keyword search or category filter")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> getAllProducts(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category
    ) {
        List<ProductResponse> products = productService.getAllProducts(search, category);
        return ResponseEntity.ok(ApiResponse.success("Products retrieved successfully", products));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get product by ID", description = "Retrieve detailed information of a specific product")
    public ResponseEntity<ApiResponse<ProductResponse>> getProductById(@PathVariable String id) {
        ProductResponse product = productService.getProductById(id);
        return ResponseEntity.ok(ApiResponse.success("Product found", product));
    }

    @PostMapping
    @Operation(summary = "Create a new product", description = "Validate and add a new product into the database")
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(@Valid @RequestBody ProductRequest request) {
        ProductResponse created = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Product created successfully", created));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing product", description = "Update details of an existing product")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(
            @PathVariable String id,
            @Valid @RequestBody ProductRequest request
    ) {
        ProductResponse updated = productService.updateProduct(id, request);
        return ResponseEntity.ok(ApiResponse.success("Product updated successfully", updated));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a product", description = "Remove a product from the database by ID")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable String id) {
        productService.deleteProduct(id);
        return ResponseEntity.ok(ApiResponse.success("Product deleted successfully", null));
    }
}
