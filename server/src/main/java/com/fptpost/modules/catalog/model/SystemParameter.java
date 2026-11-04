package com.fptpost.modules.catalog.model;

import com.fptpost.modules.catalog.enums.ParameterValueType;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@Entity
@Table(name = "system_parameters", uniqueConstraints = {
        @UniqueConstraint(name = "uq_system_parameters_key", columnNames = "parameter_key"),
        @UniqueConstraint(name = "uq_system_parameters_prm_id", columnNames = "prm_id"),
        @UniqueConstraint(name = "uq_system_parameters_public_id", columnNames = "public_id")})
public class SystemParameter {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "public_id", nullable = false, updatable = false, columnDefinition = "uniqueidentifier")
    private UUID publicId;
    @Column(name = "prm_id", nullable = false, updatable = false, length = 10)
    private String prmId;
    @Column(name = "parameter_key", nullable = false, updatable = false, length = 64)
    private String key;
    @Column(name = "parameter_group", nullable = false, length = 100, columnDefinition = "nvarchar(100)")
    private String group;
    @Column(nullable = false, length = 200, columnDefinition = "nvarchar(200)")
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", nullable = false, length = 10)
    private ParameterValueType valueType;
    @Column(name = "parameter_value", nullable = false, length = 1000, columnDefinition = "nvarchar(1000)")
    private String value;
    @Column(length = 100, columnDefinition = "nvarchar(100)")
    private String unit;
    @Column(length = 1000, columnDefinition = "nvarchar(1000)")
    private String description;
    @Version @Column(nullable = false)
    private Long version;
    @Column(name = "effective_from", nullable = false, columnDefinition = "datetime2(6)")
    private Instant effectiveFrom;
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "datetime2(6)")
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false, columnDefinition = "datetime2(6)")
    private Instant updatedAt;
    @Column(name = "updated_by", columnDefinition = "uniqueidentifier")
    private UUID updatedBy;
}
