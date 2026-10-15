package com.fptpost.modules.identity.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "user_profiles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfile {
    @Id @Column(name = "user_id")
    private Long userId;

    @MapsId @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "full_name", nullable = false, columnDefinition = "nvarchar(100)")
    private String fullName;

    @Column(name = "adult_confirmed_at", nullable = false, columnDefinition = "datetime2(6)")
    private Instant adultConfirmedAt;
}
