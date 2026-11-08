package com.fptpost.modules.backoffice.model;

import com.fptpost.modules.identity.enums.UserStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** Lịch sử khóa tài khoản (UC-56); bản ghi chưa có unlockedAt là lần khóa đang hiệu lực. */
@Entity
@Table(name = "account_locks", indexes = {
        @Index(name = "idx_account_locks_user", columnList = "user_id, unlocked_at"),
        @Index(name = "idx_account_locks_expiry", columnList = "unlocked_at, locked_until")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountLock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Trạng thái được khôi phục khi mở khóa. */
    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", nullable = false, length = 20)
    private UserStatus previousStatus;

    @Column(nullable = false, length = 500, columnDefinition = "nvarchar(500)")
    private String reason;

    @Column(name = "locked_by", nullable = false)
    private Long lockedBy;

    @Column(name = "locked_at", nullable = false, columnDefinition = "datetime2(6)")
    private Instant lockedAt;

    /** Null nghĩa là khóa vĩnh viễn cho tới khi Admin mở khóa. */
    @Column(name = "locked_until", columnDefinition = "datetime2(6)")
    private Instant lockedUntil;

    @Column(name = "unlocked_by")
    private Long unlockedBy;

    @Column(name = "unlocked_at", columnDefinition = "datetime2(6)")
    private Instant unlockedAt;

    @Column(name = "unlock_reason", length = 500, columnDefinition = "nvarchar(500)")
    private String unlockReason;
}
