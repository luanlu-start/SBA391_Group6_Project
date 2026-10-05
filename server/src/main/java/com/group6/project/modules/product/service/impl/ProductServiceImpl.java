package com.group6.project.modules.product.service.impl;

import com.group6.project.common.exception.ResourceNotFoundException;
import com.group6.project.modules.product.dto.ProductRequest;
import com.group6.project.modules.product.dto.ProductResponse;
import com.group6.project.modules.product.model.Product;
import com.group6.project.modules.product.repository.ProductRepository;
import com.group6.project.modules.product.service.ProductService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductServiceImpl.class);

    private final ProductRepository productRepository;

    // In-memory fallback cache for development resilience when MongoDB is not running locally
    private final Map<String, Product> fallbackStore = new ConcurrentHashMap<>();
    private boolean useMongoDb = true;

    @PostConstruct
    public void init() {
        // Initialize seed data for smooth demo out-of-the-box
        List<Product> seedProducts = List.of(
                Product.builder()
                        .id("prod-1")
                        .name("MacBook Pro M3 Max")
                        .description("High-performance laptop for enterprise software engineers")
                        .price(3199.0)
                        .category("Electronics")
                        .stock(15)
                        .status("ACTIVE")
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build(),
                Product.builder()
                        .id("prod-2")
                        .name("Keychron Q1 Pro Mechanical Keyboard")
                        .description("Custom wireless mechanical keyboard with QMK/VIA support")
                        .price(199.0)
                        .category("Accessories")
                        .stock(40)
                        .status("ACTIVE")
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build(),
                Product.builder()
                        .id("prod-3")
                        .name("Dell UltraSharp 32 4K Monitor")
                        .description("IPS Black technology display with 98% DCI-P3 color gamut")
                        .price(899.0)
                        .category("Electronics")
                        .stock(8)
                        .status("ACTIVE")
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build()
        );

        try {
            if (productRepository.count() == 0) {
                productRepository.saveAll(seedProducts);
                log.info("MongoDB initialized with {} seed products.", seedProducts.size());
            }
        } catch (Exception e) {
            log.warn("MongoDB connection unavailable ({}). Activated in-memory resilient fallback for local demo.", e.getMessage());
            useMongoDb = false;
            for (Product p : seedProducts) {
                fallbackStore.put(p.getId(), p);
            }
        }
    }

    @Override
    public List<ProductResponse> getAllProducts(String search, String category) {
        List<Product> products;
        try {
            if (useMongoDb) {
                if (category != null && !category.isBlank()) {
                    products = productRepository.findByCategory(category);
                } else if (search != null && !search.isBlank()) {
                    products = productRepository.findByNameContainingIgnoreCase(search);
                } else {
                    products = productRepository.findAll();
                }
            } else {
                products = getFromFallback(search, category);
            }
        } catch (Exception e) {
            log.warn("Failed reading from MongoDB ({}), falling back to memory store", e.getMessage());
            useMongoDb = false;
            products = getFromFallback(search, category);
        }

        return products.stream().map(ProductResponse::fromEntity).toList();
    }

    private List<Product> getFromFallback(String search, String category) {
        return fallbackStore.values().stream()
                .filter(p -> category == null || category.isBlank() || p.getCategory().equalsIgnoreCase(category))
                .filter(p -> search == null || search.isBlank() || p.getName().toLowerCase().contains(search.toLowerCase()))
                .sorted(Comparator.comparing(Product::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    @Override
    public ProductResponse getProductById(String id) {
        Product product = null;
        try {
            if (useMongoDb) {
                product = productRepository.findById(id).orElse(null);
            }
        } catch (Exception e) {
            log.warn("MongoDB read failed ({})", e.getMessage());
            useMongoDb = false;
        }

        if (product == null) {
            product = fallbackStore.get(id);
        }

        if (product == null) {
            throw new ResourceNotFoundException("Product", "id", id);
        }

        return ProductResponse.fromEntity(product);
    }

    @Override
    public ProductResponse createProduct(ProductRequest request) {
        Product product = Product.builder()
                .id(UUID.randomUUID().toString())
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .category(request.getCategory())
                .stock(request.getStock())
                .status(request.getStatus() != null ? request.getStatus() : "ACTIVE")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        try {
            if (useMongoDb) {
                Product saved = productRepository.save(product);
                return ProductResponse.fromEntity(saved);
            }
        } catch (Exception e) {
            log.warn("MongoDB save failed ({}), saving to in-memory store", e.getMessage());
            useMongoDb = false;
        }

        fallbackStore.put(product.getId(), product);
        return ProductResponse.fromEntity(product);
    }

    @Override
    public ProductResponse updateProduct(String id, ProductRequest request) {
        Product existing = null;
        try {
            if (useMongoDb) {
                existing = productRepository.findById(id).orElse(null);
            }
        } catch (Exception e) {
            useMongoDb = false;
        }

        if (existing == null) {
            existing = fallbackStore.get(id);
        }

        if (existing == null) {
            throw new ResourceNotFoundException("Product", "id", id);
        }

        existing.setName(request.getName());
        existing.setDescription(request.getDescription());
        existing.setPrice(request.getPrice());
        existing.setCategory(request.getCategory());
        existing.setStock(request.getStock());
        if (request.getStatus() != null) {
            existing.setStatus(request.getStatus());
        }
        existing.setUpdatedAt(Instant.now());

        try {
            if (useMongoDb) {
                Product saved = productRepository.save(existing);
                return ProductResponse.fromEntity(saved);
            }
        } catch (Exception e) {
            useMongoDb = false;
        }

        fallbackStore.put(existing.getId(), existing);
        return ProductResponse.fromEntity(existing);
    }

    @Override
    public void deleteProduct(String id) {
        boolean found = false;
        try {
            if (useMongoDb && productRepository.existsById(id)) {
                productRepository.deleteById(id);
                found = true;
            }
        } catch (Exception e) {
            useMongoDb = false;
        }

        if (fallbackStore.containsKey(id)) {
            fallbackStore.remove(id);
            found = true;
        }

        if (!found) {
            throw new ResourceNotFoundException("Product", "id", id);
        }
    }
}
