package com.fptpost.modules.order.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Nationalized;

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
@Table(name = "orders", indexes = {
    @Index(name = "idx_orders_order_code", columnList = "order_code", unique = true),
    @Index(name = "idx_orders_sender_id", columnList = "sender_id"),
    @Index(name = "idx_orders_status", columnList = "status"),
    @Index(name = "idx_orders_created_at", columnList = "created_at")
})
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_code", nullable = false, unique = true, length = 50)
    private String orderCode;

    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    @Column(name = "courier_id")
    private Long courierId;

    @Nationalized
    @Column(name = "pickup_address", nullable = false, length = 500, columnDefinition = "nvarchar(500)")
    private String pickupAddress;

    @Column(name = "pickup_latitude", precision = 10, scale = 7)
    private BigDecimal pickupLatitude;

    @Column(name = "pickup_longitude", precision = 10, scale = 7)
    private BigDecimal pickupLongitude;

    @Nationalized
    @Column(name = "delivery_address", nullable = false, length = 500, columnDefinition = "nvarchar(500)")
    private String deliveryAddress;

    @Column(name = "delivery_latitude", precision = 10, scale = 7)
    private BigDecimal deliveryLatitude;

    @Column(name = "delivery_longitude", precision = 10, scale = 7)
    private BigDecimal deliveryLongitude;

    @Nationalized
    @Column(name = "receiver_name", nullable = false, length = 100, columnDefinition = "nvarchar(100)")
    private String receiverName;

    @Column(name = "receiver_phone", nullable = false, length = 20)
    private String receiverPhone;

    @Nationalized
    @Column(name = "product_name", nullable = false, length = 255, columnDefinition = "nvarchar(255)")
    private String productName;

    @Nationalized
    @Column(name = "product_description", length = 1000, columnDefinition = "nvarchar(1000)")
    private String productDescription;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "weight_kg", nullable = false, precision = 10, scale = 3)
    private BigDecimal weightKg;

    @Column(name = "declared_value", precision = 15, scale = 2)
    private BigDecimal declaredValue;

    @Column(name = "price_suggested", precision = 15, scale = 2)
    private BigDecimal priceSuggested;

    @Column(name = "price_agreed", precision = 15, scale = 2)
    private BigDecimal priceAgreed;

    @Column(name = "fee_rate_locked", precision = 5, scale = 4)
    private BigDecimal feeRateLocked;
    @Column(name = "distance_km", precision = 10, scale = 2)
    private BigDecimal distanceKm;

    @Enumerated(EnumType.STRING)
    @Column(name = "urgency", length = 30)
    private DeliveryUrgency urgency;

    @Nationalized
    @Column(name = "delivery_note", length = 500, columnDefinition = "nvarchar(500)")
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
    private List<OrderImage> images = new ArrayList<>();

    public void addImage(OrderImage image) {
        images.add(image);
        image.setOrder(this);
    }

    public void removeImage(OrderImage image) {
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
        if (this.urgency == null) {
            this.urgency = DeliveryUrgency.STANDARD;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
