package com.fptpost.modules.identity.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

@Entity @Table(name = "consents")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Consent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(name = "policy_type", nullable = false, length = 30)
    private String policyType;
    @Column(name = "policy_version", nullable = false, length = 50)
    private String policyVersion;
    @Column(name = "accepted_at", nullable = false, columnDefinition = "datetime2(6)")
    private Instant acceptedAt;
    @Column(nullable = false, length = 45)
    private String ip;
}
