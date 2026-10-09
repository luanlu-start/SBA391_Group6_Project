package com.fptpost.modules.identity.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

@Entity @Table(name = "blacklist")
@Getter @Setter @NoArgsConstructor
public class BlacklistEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 20)
    private String kind;
    @Column(name = "value_hash", nullable = false, length = 64)
    private String valueHash;
    @Column(nullable = false, columnDefinition = "nvarchar(500)")
    private String reason;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "created_by")
    private User createdBy;
    @Column(name = "created_at", nullable = false, columnDefinition = "datetime2(6)")
    private Instant createdAt;
}
