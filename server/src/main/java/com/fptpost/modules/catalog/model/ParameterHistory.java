package com.fptpost.modules.catalog.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@Entity
@Table(name = "parameter_history", uniqueConstraints = {
        @UniqueConstraint(name = "uq_parameter_history_public_id", columnNames = "public_id"),
        @UniqueConstraint(name = "uq_parameter_history_version", columnNames = {"parameter_id", "parameter_version"})},
        indexes = @Index(name = "ix_parameter_history_parameter_version", columnList = "parameter_id, parameter_version DESC"))
public class ParameterHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "public_id", nullable = false, updatable = false, columnDefinition = "uniqueidentifier")
    private UUID publicId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parameter_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_parameter_history_parameter"))
    private SystemParameter parameter;
    @Column(name = "old_value", nullable = false, updatable = false, length = 1000, columnDefinition = "nvarchar(1000)")
    private String oldValue;
    @Column(name = "new_value", nullable = false, updatable = false, length = 1000, columnDefinition = "nvarchar(1000)")
    private String newValue;
    @Column(name = "changed_by", nullable = false, updatable = false, columnDefinition = "uniqueidentifier")
    private UUID changedBy;
    @Column(name = "changed_at", nullable = false, updatable = false, columnDefinition = "datetime2(6)")
    private Instant changedAt;
    @Column(name = "parameter_version", nullable = false, updatable = false)
    private long version;
}
