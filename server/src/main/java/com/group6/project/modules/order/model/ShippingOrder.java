package com.group6.project.modules.order.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "images")
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "shipping_orders", indexes = {
    @Index(name = "idx_shipping_orders_order_code", columnList = "order_code", unique = true),
    @Index(name = "idx_shipping_orders_sender_id", columnList = "sender_id"),
    @Index(name = "idx_shipping_orders_status", columnList = "status"),
    @Index(name = "idx_shipping_orders_created_at", columnList = "created_at")
})
public class ShippingOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_code", nullable = false, unique = true, length = 50)
    private String orderCode;

    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    @Column(name = "pickup_address", nullable = false, length = 500)
    private String pickupAddress;

    @Column(name = "delivery_address", nullable = false, length = 500)
    private String deliveryAddress;

    @Column(name = "receiver_name", nullable = false, length = 100)
    private String receiverName;

    @Column(name = "receiver_phone", nullable = false, length = 20)
    private String receiverPhone;

    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    @Column(name = "product_description", length = 1000)
    private String productDescription;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "weight_kg", nullable = false, precision = 10, scale = 3)
    private BigDecimal weightKg;

    @Column(name = "declared_value", precision = 15, scale = 2)
    private BigDecimal declaredValue;

    @Column(name = "delivery_note", length = 500)
    private String deliveryNote;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private OrderStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ShippingOrderImage> images = new ArrayList<>();

    public void addImage(ShippingOrderImage image) {
        images.add(image);
        image.setOrder(this);
    }

    public void removeImage(ShippingOrderImage image) {
        images.remove(image);
        image.setOrder(null);
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
        if (this.status == null) {
            this.status = OrderStatus.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
