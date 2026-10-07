package com.group6.project.modules.product.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.persistence.*;
import org.hibernate.annotations.Check;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "products", indexes = @Index(name = "ix_products_created_at_id", columnList = "created_at DESC, id ASC"))
@Check(constraints = "price >= 0 AND stock >= 0 AND status IN ('ACTIVE', 'INACTIVE')")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "uniqueidentifier")
    private UUID id;

    @Column(nullable = false, length = 100, columnDefinition = "nvarchar(100)")
    private String name;

    @Column(length = 500, columnDefinition = "nvarchar(500)")
    private String description;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, length = 100, columnDefinition = "nvarchar(100)")
    private String category;

    @Column(nullable = false)
    private Integer stock;

    @Builder.Default
    @Column(nullable = false, length = 10)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "datetime2(6)")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "datetime2(6)")
    private Instant updatedAt;
}
