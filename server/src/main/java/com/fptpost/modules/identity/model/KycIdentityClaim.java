package com.fptpost.modules.identity.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.*;
import java.util.UUID;

@Entity @Table(name = "kyc_identity_claims")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class KycIdentityClaim {
    @Id @Column(name = "id_number_hash", length = 64) private String idNumberHash;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false) private User user;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "kyc_id", nullable = false) private KycRequest kyc;
    @Version private Long version;
}
