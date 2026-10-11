package com.fptpost.modules.identity.model;

import com.fptpost.modules.identity.enums.UserStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "public_id", nullable = false, unique = true, updatable = false, columnDefinition = "uniqueidentifier")
    private UUID publicId;
    @Column(nullable = false, unique = true, columnDefinition = "nvarchar(254)")
    private String email;
    @Column(nullable = false, unique = true, length = 16)
    private String phone;
    @Column(name = "password_hash", nullable = false, length = 60)
    private String passwordHash;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;
    @Column(name = "email_verified_at", columnDefinition = "datetime2(6)")
    private Instant emailVerifiedAt;
    @Column(name = "phone_verified_at", columnDefinition = "datetime2(6)")
    private Instant phoneVerifiedAt;
    @Column(name = "fpt_badge", nullable = false)
    private boolean fptBadge;
    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "datetime2(6)")
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false, columnDefinition = "datetime2(6)")
    private Instant updatedAt;
    @Version
    private long version;
    @Builder.Default
    @ManyToMany
    @JoinTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();
}
