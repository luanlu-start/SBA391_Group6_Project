package com.group6.project.modules.product.service;

import com.group6.project.common.exception.AppException;
import com.group6.project.common.response.PageResponse;
import com.group6.project.modules.product.dto.ProductSearchRequest;
import com.group6.project.modules.product.dto.ProductRequest;
import com.group6.project.modules.product.dto.ProductResponse;
import com.group6.project.modules.product.exception.ProductErrorCode;
import com.group6.project.modules.product.mapper.ProductMapper;
import com.group6.project.modules.product.model.Product;
import com.group6.project.modules.product.repository.ProductRepository;
import com.group6.project.modules.product.repository.ProductSpecification;
import com.group6.project.modules.activity.event.ProductActivityEvent;
import org.springframework.context.ApplicationEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {
    private final ProductRepository productRepository;
    private final ProductMapper productMapper;
    private final ApplicationEventPublisher eventPublisher;

    public PageResponse<ProductResponse> getAllProducts(ProductSearchRequest request) {
        return PageResponse.from(productRepository.findAll(ProductSpecification.filter(request), request.toPageable())
                .map(productMapper::toResponse));
    }

    public ProductResponse getProductById(UUID id) {
        return productMapper.toResponse(findProductOrThrow(id));
    }

    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        Product product = productMapper.toEntity(request);
        product.setCreatedAt(Instant.now());
        product.setUpdatedAt(product.getCreatedAt());
        Product saved = productRepository.save(product);
        publishActivity("CREATED", saved);
        return productMapper.toResponse(saved);
    }

    @Transactional
    public ProductResponse updateProduct(UUID id, ProductRequest request) {
        Product product = findProductOrThrow(id);
        productMapper.updateEntity(request, product);
        product.setUpdatedAt(Instant.now());
        Product saved = productRepository.save(product);
        publishActivity("UPDATED", saved);
        return productMapper.toResponse(saved);
    }

    @Transactional
    public void deleteProduct(UUID id) {
        Product product = findProductOrThrow(id);
        productRepository.deleteById(product.getId());
        publishActivity("DELETED", product);
    }

    private void publishActivity(String action, Product product) {
        eventPublisher.publishEvent(new ProductActivityEvent(action, product.getId(), product.getName(), Instant.now()));
    }

    private Product findProductOrThrow(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new AppException(ProductErrorCode.PRODUCT_NOT_FOUND));
    }
}
