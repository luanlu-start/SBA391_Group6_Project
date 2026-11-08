package com.fptpost.modules.reputation.service;

import com.fptpost.modules.dispute.event.ComplaintClosedEvent;
import com.fptpost.modules.reputation.enums.TrustScoreReason;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Tính lại điểm người giao sau khi khiếu nại kết thúc; lỗi ở đây không ảnh hưởng hồ sơ đã đóng, job đêm sẽ bù. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReputationEventListener {

    private final TrustScoreService trustScoreService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onComplaintClosed(ComplaintClosedEvent event) {
        if (event.courierId() == null) return;
        try {
            trustScoreService.recalculateSeparately(event.courierId(), TrustScoreReason.COMPLAINT_CLOSED);
        } catch (RuntimeException exception) {
            log.warn("Trust score update after complaint {} failed; the nightly run will retry", event.complaintId(), exception);
        }
    }
}
