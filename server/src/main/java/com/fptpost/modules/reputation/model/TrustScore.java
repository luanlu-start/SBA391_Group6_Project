package com.fptpost.modules.reputation.model;

import com.fptpost.modules.identity.enums.CourierTier;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/** Điểm uy tín hiện tại và các chỉ số thành phần của một người giao (DB-038: trust_scores). */
@Entity
@Table(name = "trust_scores", indexes = @Index(name = "idx_trust_scores_score", columnList = "score"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrustScore {

    @Id
    @Column(name = "courier_id")
    private Long courierId;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal score;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CourierTier tier;

    /** Tỷ lệ giao đúng hạn đã làm mượt; null khi chưa có dữ liệu xác nhận giao hàng. */
    @Column(name = "ontime_rate", precision = 5, scale = 4)
    private BigDecimal ontimeRate;

    @Column(name = "rating_avg", precision = 3, scale = 2)
    private BigDecimal ratingAverage;

    @Column(name = "rating_count", nullable = false)
    private long ratingCount;

    @Column(name = "completion_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal completionRate;

    @Column(name = "complaint_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal complaintRate;

    @Column(name = "completed_orders", nullable = false)
    private long completedOrders;

    @Column(name = "lost_complaints", nullable = false)
    private long lostComplaints;

    @Column(name = "recent_cancellations", nullable = false)
    private long recentCancellations;

    /** Đã báo Admin xem xét khóa khi điểm xuống dưới TRUST_REVIEW; xóa khi điểm hồi phục. */
    @Column(name = "review_flagged_at", columnDefinition = "datetime2(6)")
    private Instant reviewFlaggedAt;

    @Column(name = "restriction_noticed_at", columnDefinition = "datetime2(6)")
    private Instant restrictionNoticedAt;

    @Column(name = "calculated_at", nullable = false, columnDefinition = "datetime2(6)")
    private Instant calculatedAt;

    @Version
    private long version;
}
