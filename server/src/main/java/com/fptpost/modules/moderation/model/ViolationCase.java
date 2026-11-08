package com.fptpost.modules.moderation.model;

import com.fptpost.modules.moderation.enums.ViolationAction;
import com.fptpost.modules.moderation.enums.ViolationCaseStatus;
import com.fptpost.modules.moderation.enums.ViolationReason;
import com.fptpost.modules.moderation.enums.ViolationTargetType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/** Hồ sơ vi phạm theo từng đối tượng; báo cáo trùng khi hồ sơ còn mở được gộp vào đây (UC-55 A1). */
@Entity
@Table(name = "violation_cases", indexes = {
        @Index(name = "idx_violation_cases_target", columnList = "target_type, target_id, status"),
        @Index(name = "idx_violation_cases_queue", columnList = "status, report_count, created_at"),
        @Index(name = "idx_violation_cases_respondent", columnList = "respondent_id, status")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ViolationCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, updatable = false, columnDefinition = "uniqueidentifier")
    private UUID publicId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private ViolationTargetType targetType;

    @Column(name = "target_id", nullable = false, length = 64)
    private String targetId;

    @Column(name = "target_label", length = 300, columnDefinition = "nvarchar(300)")
    private String targetLabel;

    @Column(name = "related_order_id")
    private Long relatedOrderId;

    /** Người bị báo cáo: chủ tài khoản, người gửi/người giao của đơn hoặc tác giả tin nhắn. */
    @Column(name = "respondent_id")
    private Long respondentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "latest_reason", nullable = false, length = 30)
    private ViolationReason latestReason;

    @Column(name = "report_count", nullable = false)
    private int reportCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ViolationCaseStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolution", length = 20)
    private ViolationAction resolution;

    @Column(name = "resolution_note", length = 500, columnDefinition = "nvarchar(500)")
    private String resolutionNote;

    @Column(name = "handled_by")
    private Long handledBy;

    @Column(name = "handled_at", columnDefinition = "datetime2(6)")
    private Instant handledAt;

    @Column(name = "created_at", nullable = false, columnDefinition = "datetime2(6)")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "datetime2(6)")
    private Instant updatedAt;

    @Version
    private long version;
}
