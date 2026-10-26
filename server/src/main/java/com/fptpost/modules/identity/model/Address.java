package com.fptpost.modules.identity.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;
import java.math.BigDecimal;

@Entity @Table(name = "addresses", indexes = @Index(name = "ix_addresses_user", columnList = "user_id"))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Address {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "public_id", nullable = false, unique = true, updatable = false, columnDefinition = "uniqueidentifier") private UUID publicId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false) private User user;
    @Column(nullable = false, columnDefinition = "nvarchar(60)") private String label;
    @Column(name = "address_text", nullable = false, columnDefinition = "nvarchar(500)") private String addressText;
    @Column(nullable = false, precision = 10, scale = 7) private BigDecimal lat;
    @Column(nullable = false, precision = 10, scale = 7) private BigDecimal lng;
    @Column(name = "is_default", nullable = false) private boolean defaultAddress;
    @Version private long version;
}
