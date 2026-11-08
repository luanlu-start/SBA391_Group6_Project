package com.fptpost.modules.moderation.model;

import com.fptpost.modules.moderation.enums.ModerationKeywordLevel;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "moderation_keywords")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModerationKeyword {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100, columnDefinition = "nvarchar(100)")
    private String term;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ModerationKeywordLevel level;

    @Column(name = "created_at", nullable = false, columnDefinition = "datetime2(6)")
    private Instant createdAt;
}
