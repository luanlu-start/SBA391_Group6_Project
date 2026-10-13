package com.fptpost.modules.order.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Entity lưu trữ hình ảnh đính kèm của đơn hàng (ảnh kiện hàng, biên nhận,...).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "order")
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "shipping_order_images", indexes = {
    @Index(name = "idx_shipping_order_images_order_id", columnList = "order_id")
})
public class OrderImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "image_url", nullable = false, length = 1000)
    private String imageUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}
