package com.fptpost.modules.product.controller;

import com.fptpost.common.response.ApiResponse;
import com.fptpost.modules.product.dto.ProductRequest;
import com.fptpost.modules.product.dto.ProductResponse;
import com.fptpost.modules.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.fptpost.common.response.PageResponse;
import com.fptpost.modules.product.dto.ProductSearchRequest;
import org.springdoc.core.annotations.ParameterObject;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Product Management REST API endpoints")
public class ProductController {

    private final ProductService productService;

    @GetMapping
    @Operation(summary = "Search products", description = "Optional filters combined with pagination and sorting")
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> getAllProducts(
            @Valid @ModelAttribute @ParameterObject ProductSearchRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Products retrieved successfully",
                productService.getAllProducts(request)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get product by ID", description = "Retrieve detailed information of a specific product")
    public ResponseEntity<ApiResponse<ProductResponse>> getProductById(@PathVariable UUID id) {
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
            @PathVariable UUID id,
            @Valid @RequestBody ProductRequest request
    ) {
        ProductResponse updated = productService.updateProduct(id, request);
        return ResponseEntity.ok(ApiResponse.success("Product updated successfully", updated));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a product", description = "Remove a product from the database by ID")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable UUID id) {
        productService.deleteProduct(id);
        return ResponseEntity.ok(ApiResponse.success("Product deleted successfully", null));
    }
}
