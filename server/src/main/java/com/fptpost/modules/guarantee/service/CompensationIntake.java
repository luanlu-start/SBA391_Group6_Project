package com.fptpost.modules.guarantee.service;

import com.fptpost.modules.dispute.enums.ComplaintOutcome;
import com.fptpost.modules.dispute.event.ComplaintClosedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Nhận phán quyết cuối cùng của UC-51 trong cùng giao dịch, để hồ sơ đóng và khoản chờ duyệt cùng được ghi nhận. */
@Component
@RequiredArgsConstructor
class CompensationIntake {

    private final GuaranteeFundService guaranteeFundService;

    @EventListener
    public void onComplaintClosed(ComplaintClosedEvent event) {
        if (event.outcome() == ComplaintOutcome.COURIER_FAVOR) return;
        guaranteeFundService.register(event.complaintId(), event.complaintPublicId(), event.orderId(), event.senderId(),
                event.compensationAmount(), event.ruledAt());
    }
}
