package com.group6.project.modules.product.dto;

import com.group6.project.modules.product.model.Product;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponse {

    private String id;
    private String name;
    private String description;
    private Double price;
    private String category;
    private Integer stock;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;

    public static ProductResponse fromEntity(Product product) {
        if (product == null) return null;
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .category(product.getCategory())
                .stock(product.getStock())
                .status(product.getStatus())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }
}
