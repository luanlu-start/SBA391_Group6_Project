package com.fptpost.modules.dispute.service;

import com.fptpost.modules.dispute.model.Complaint;
import com.fptpost.modules.dispute.repository.ComplaintDirectory;
import com.fptpost.modules.notification.enums.NotificationCategory;
import com.fptpost.modules.notification.service.NotificationPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Objects;
import java.util.UUID;

/** Thông báo của khiếu nại; khóa sự kiện gắn mã hồ sơ để mỗi người chỉ nhận một lần cho mỗi bước. */
@Component
@RequiredArgsConstructor
class ComplaintNotifier {

    private final NotificationPublisher notifications;
    private final ComplaintDirectory directory;

    void parties(Complaint complaint, Collection<Long> userIds, String step, String title, String body) {
        userIds.stream().filter(Objects::nonNull).distinct().forEach(userId -> directory.reachable(userId)
                .ifPresent(recipient -> send(recipient, complaint, step, title, body, "/complaints/" + complaint.getPublicId())));
    }

    void staff(Complaint complaint, String role, UUID except, String step, String title, String body) {
        for (UUID recipient : directory.activeStaff(role)) {
            if (recipient.equals(except)) continue;
            send(recipient, complaint, step, title, body, "/mod/complaints/" + complaint.getPublicId());
        }
    }

    void person(Long userId, Complaint complaint, String step, String title, String body) {
        if (userId == null) return;
        directory.reachable(userId).ifPresent(recipient ->
                send(recipient, complaint, step, title, body, "/mod/complaints/" + complaint.getPublicId()));
    }

    private void send(UUID recipient, Complaint complaint, String step, String title, String body, String link) {
        notifications.publish(recipient, NotificationCategory.DISPUTE, "complaint:" + complaint.getPublicId() + ":" + step,
                title, body, link);
    }
}
