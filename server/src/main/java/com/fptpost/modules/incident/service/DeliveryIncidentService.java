package com.fptpost.modules.incident.service;

import com.fptpost.common.exception.*;
import com.fptpost.common.file.dto.ValidatedImage;
import com.fptpost.common.file.service.*;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.repository.UserRepository;
import com.fptpost.modules.identity.service.IdentityUserAccess;
import com.fptpost.modules.incident.dto.*;
import com.fptpost.modules.incident.exception.IncidentErrorCode;
import com.fptpost.modules.incident.model.DeliveryIncident;
import com.fptpost.modules.incident.repository.*;
import com.fptpost.modules.notification.enums.NotificationCategory;
import com.fptpost.modules.notification.service.NotificationPublisher;
import com.fptpost.modules.order.exception.OrderErrorCode;
import com.fptpost.modules.order.model.*;
import com.fptpost.modules.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.time.Clock;
import java.util.*;

@Service @RequiredArgsConstructor
public class DeliveryIncidentService {
    private final DeliveryIncidentRepository incidents;
    private final OrderRepository orders;
    private final IdentityUserAccess access;
    private final UserRepository users;
    private final IncidentRecipientRepository recipients;
    private final ImageValidator images;
    private final PrivateFileStorage storage;
    private final NotificationPublisher notifications;
    private final Clock clock;

    @Transactional
    public IncidentResponse report(UUID actorId, Long orderId, IncidentRequest request, MultipartFile photo) {
        User actor = access.read(actorId);
        requireCourier(actor);
        Order order = orders.findByIdForUpdate(orderId)
                .orElseThrow(() -> new AppException(OrderErrorCode.ORDER_NOT_FOUND));
        if (!Objects.equals(actor.getId(), order.getCourierId())) denied();
        if (order.getStatus() != OrderStatus.IN_DELIVERY)
            throw new AppException(IncidentErrorCode.INCIDENT_INVALID_STATUS);
        if (request == null || request.type() == null || request.description() == null
                || request.description().isBlank() || request.description().length() > 1000)
            throw new AppException(GlobalErrorCode.INVALID_REQUEST);
        ValidatedImage image = photo == null ? null : images.validate(photo);
        DeliveryIncident incident = incidents.save(DeliveryIncident.builder().order(order).reporterId(actor.getId())
                .type(request.type()).description(request.description().strip()).createdAt(clock.instant())
                .evidenceKey(image == null ? null : storage.put(image.bytes()))
                .evidenceMediaType(image == null ? null : image.mediaType()).build());

        // Outbox entries and incident commit together; no external sending inside this transaction.
        Set<UUID> targets = new TreeSet<>(recipients.findActiveModerators());
        users.findById(order.getSenderId()).filter(u -> !u.isDeleted())
                .map(User::getPublicId).ifPresent(targets::add);
        for (UUID target : targets) {
            notifications.publish(target, NotificationCategory.ORDER, "INCIDENT:" + incident.getId(),
                    "Sự cố giao hàng", "Đơn " + order.getOrderCode() + " có báo cáo sự cố. Vui lòng xem lịch sử sự cố.",
                    "/orders/" + order.getId() + "/incidents");
        }
        return IncidentResponse.from(incident);
    }

    @Transactional(readOnly = true)
    public Page<IncidentResponse> history(UUID actorId, Long orderId, int page, int size) {
        authorizeRead(actorId, orderId);
        if (page < 0 || size < 1 || size > 50) throw new AppException(GlobalErrorCode.INVALID_REQUEST);
        return incidents.findByOrderIdOrderByCreatedAtDescIdDesc(orderId, PageRequest.of(page, size))
                .map(IncidentResponse::from);
    }

    @Transactional(readOnly = true)
    public ValidatedImage evidence(UUID actorId, Long orderId, Long incidentId) {
        authorizeRead(actorId, orderId);
        DeliveryIncident incident = incidents.findByIdAndOrderId(incidentId, orderId)
                .orElseThrow(() -> new AppException(IncidentErrorCode.INCIDENT_NOT_FOUND));
        if (incident.getEvidenceKey() == null) throw new AppException(IncidentErrorCode.INCIDENT_NOT_FOUND);
        return new ValidatedImage(storage.read(incident.getEvidenceKey()), incident.getEvidenceMediaType());
    }

    private void authorizeRead(UUID actorId, Long orderId) {
        User actor = access.read(actorId);
        Order order = orders.findById(orderId).orElseThrow(() -> new AppException(OrderErrorCode.ORDER_NOT_FOUND));
        boolean staff = actor.getRoles().stream().anyMatch(r -> Set.of("ADMIN", "MODERATOR").contains(r.getCode()));
        if (!staff && !Objects.equals(actor.getId(), order.getSenderId())
                && !Objects.equals(actor.getId(), order.getCourierId())) denied();
    }

    private void requireCourier(User user) {
        if (user.getRoles().stream().noneMatch(r -> "COURIER".equals(r.getCode()))) denied();
    }

    private void denied() { throw new AppException(IncidentErrorCode.INCIDENT_ACCESS_DENIED); }
}
