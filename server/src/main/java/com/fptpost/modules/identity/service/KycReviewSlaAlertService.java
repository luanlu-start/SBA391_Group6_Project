package com.fptpost.modules.identity.service;

import com.fptpost.modules.identity.enums.KycStatus;
import com.fptpost.modules.identity.repository.*;
import com.fptpost.modules.notification.enums.NotificationCategory;
import com.fptpost.modules.notification.service.NotificationPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;

@Service @RequiredArgsConstructor
public class KycReviewSlaAlertService {
    private final KycRequestRepository requests;
    private final UserRepository users;
    private final KycReviewAdminDirectory administrators;
    private final KycReviewSlaPolicy sla;
    private final NotificationPublisher notifications;

    @Transactional
    public boolean alert(KycReviewSearchRepository.Candidate candidate, long hours, Instant now) {
        if (users.findByPublicId(candidate.applicantId()).isEmpty()) return false;
        var recipients = administrators.lockApplicantAndAdministrators(candidate.applicantId());
        var request = requests.findByPublicIdForUpdate(candidate.requestId()).orElse(null);
        if (request == null || request.getStatus() != KycStatus.PENDING || Objects.equals(request.getSlaAlertedHours(), hours)
                || !request.getUser().getPublicId().equals(candidate.applicantId())) return false;
        Instant dueAt = sla.dueAt(request.getSubmittedAt(), hours);
        if (now.isBefore(dueAt)) return false;
        if (recipients.isEmpty()) return false;
        String eventKey = "kyc:overdue:" + request.getPublicId() + ":" + dueAt.getEpochSecond();
        for (var recipient : recipients) {
            notifications.publish(recipient, NotificationCategory.KYC, eventKey, "Hồ sơ xác minh quá hạn duyệt",
                    "Một hồ sơ đang chờ xử lý đã vượt thời hạn duyệt. Vui lòng kiểm tra hàng đợi xác minh.",
                    "/mod/kyc?request=" + request.getPublicId());
        }
        request.setSlaAlertedHours(hours); request.setSlaAlertedAt(now);
        requests.saveAndFlush(request);
        return true;
    }
}
