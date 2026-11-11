package com.fptpost.modules.deliveryreturn.service;

import com.fptpost.common.exception.*;
import com.fptpost.common.file.dto.ValidatedImage;
import com.fptpost.common.file.service.*;
import com.fptpost.modules.catalog.service.ParameterService;
import com.fptpost.modules.deliveryreturn.dto.*;
import com.fptpost.modules.deliveryreturn.exception.*;
import com.fptpost.modules.deliveryreturn.model.*;
import com.fptpost.modules.deliveryreturn.repository.*;
import com.fptpost.modules.identity.enums.UserStatus;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.repository.UserRepository;
import com.fptpost.modules.identity.service.IdentityUserAccess;
import com.fptpost.modules.notification.enums.NotificationCategory;
import com.fptpost.modules.notification.service.NotificationPublisher;
import com.fptpost.modules.order.exception.OrderErrorCode;
import com.fptpost.modules.order.model.Order;
import com.fptpost.modules.order.model.OrderStatus;
import com.fptpost.modules.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;
import static com.fptpost.modules.deliveryreturn.exception.DeliveryReturnErrorCode.*;

@Service @RequiredArgsConstructor
public class FailedDeliveryService {
    private final OrderRepository orders;
    private final DeliveryReturnRepository cases;
    private final DeliveryContactAttemptRepository contacts;
    private final DeliveryReturnHistoryRepository history;
    private final IdentityUserAccess access;
    private final UserRepository users;
    private final ParameterService parameters;
    private final ImageValidator images;
    private final PrivateFileStorage storage;
    private final NotificationPublisher notifications;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    @Transactional(readOnly = true)
    public ReturnResponse detail(UUID actorId, Long orderId) {
        User actor = access.read(actorId);
        Order order = readOrder(orderId);
        requireReader(actor, order);
        return response(order, actor, policy());
    }

    @Transactional
    public ReturnResponse contact(UUID actorId, Long orderId, ReturnRequests.Contact input) {
        User actor = access.read(actorId);
        Order order = lockOrder(orderId);
        requireCourier(actor, order);
        if (input == null || input.requestId() == null) invalid();
        text(input.note(), 500);
        var existing = contacts.findByOrderIdAndRequestId(orderId, input.requestId());
        if (existing.isPresent()) {
            if (!Objects.equals(existing.get().getCourierId(), actor.getId())) denied();
            return response(order, actor, policy());
        }
        requireStatus(order, OrderStatus.IN_DELIVERY);
        contacts.save(DeliveryContactAttempt.builder().orderId(orderId).courierId(actor.getId()).requestId(input.requestId())
                .note(input.note().strip()).createdAt(clock.instant()).build());
        event(order, null, actor, "CONTACT_ATTEMPT", order.getStatus(), order.getStatus(), input.note().strip());
        return response(order, actor, policy());
    }

    @Transactional
    public ReturnResponse report(UUID actorId, Long orderId, ReturnRequests.Failure input, MultipartFile photo) {
        User actor = access.read(actorId);
        Order order = lockOrder(orderId);
        requireCourier(actor, order);
        requireStatus(order, OrderStatus.IN_DELIVERY);
        if (input == null || input.reason() == null) invalid();
        text(input.description(), 1000);
        Policy policy = policy();
        var attempts = unusedContacts(order, policy);
        if (attempts.size() < policy.calls()) throw new AppException(RETURN_CONTACT_REQUIRED);
        if (photo == null || photo.isEmpty()) throw new AppException(RETURN_EVIDENCE_REQUIRED);
        ValidatedImage image = images.validate(photo);
        BigDecimal fee = order.getPriceAgreed() != null ? order.getPriceAgreed() : order.getPriceSuggested();
        if (fee == null || fee.signum() <= 0) throw new AppException(RETURN_POLICY_UNAVAILABLE);
        fee = fee.setScale(0, RoundingMode.HALF_UP);
        var value = cases.save(DeliveryReturnCase.builder().order(order).courierId(actor.getId()).reason(input.reason())
                .description(input.description().strip()).state(ReturnState.RETURNING).createdAt(clock.instant())
                .requiredCalls(policy.calls()).contactWindowMinutes(policy.minutes())
                .deliveryFee(fee).returnRate(policy.rate()).returnFee(fee.multiply(policy.rate()).setScale(0, RoundingMode.HALF_UP))
                .feePolicyVersion(policy.feeVersion()).evidenceKey(storage.put(image.bytes())).evidenceMediaType(image.mediaType()).build());
        // Consume the evidence once. A later failed attempt after resume needs fresh contact records.
        attempts.forEach(attempt -> attempt.setReturnCase(value));
        contacts.saveAll(attempts);
        event(order, value, actor, "DELIVERY_FAILED", OrderStatus.IN_DELIVERY, OrderStatus.DELIVERY_FAILED, value.getDescription());
        order.setStatus(OrderStatus.RETURNING);
        var milestone = event(order, value, actor, "RETURN_STARTED", OrderStatus.DELIVERY_FAILED, OrderStatus.RETURNING,
                "Hoàn về điểm lấy hàng; phí mới là dự kiến, chưa thu hoặc giải ngân.");
        orders.save(order);
        notifyParties(order, milestone, "Giao hàng thất bại, đang hoàn về người gửi");
        return response(order, actor, policy);
    }

    @Transactional
    public ReturnResponse resume(UUID actorId, Long orderId, ReturnRequests.Resume input) {
        User actor = access.read(actorId);
        Order order = lockOrder(orderId);
        requireCourier(actor, order);
        requireStatus(order, OrderStatus.RETURNING);
        if (input == null) invalid();
        text(input.description(), 1000);
        var value = activeCase(order);
        value.setState(ReturnState.RESUMED); value.setResolvedAt(clock.instant()); invalidateCode(value);
        order.setStatus(OrderStatus.IN_DELIVERY);
        var milestone = event(order, value, actor, "DELIVERY_RESUMED", OrderStatus.RETURNING, OrderStatus.IN_DELIVERY, input.description().strip());
        orders.save(order);
        notifyParties(order, milestone, "Người nhận đã liên lạc lại, tiếp tục giao hàng");
        return response(order, actor, policy());
    }

    @Transactional
    public ReturnResponse.Code issueCode(UUID actorId, Long orderId) {
        User actor = access.read(actorId);
        Order order = lockOrder(orderId);
        requireSender(actor, order);
        requireStatus(order, OrderStatus.RETURNING);
        var value = activeCase(order);
        Instant now = clock.instant();
        if (value.getCodeIssuedAt() != null && now.isBefore(value.getCodeIssuedAt().plusSeconds(60)))
            throw new AppException(RETURN_CODE_COOLDOWN);
        String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        String salt = UUID.randomUUID().toString();
        value.setCodeSalt(salt); value.setCodeHash(hash(salt, code)); value.setCodeFailures(0);
        value.setCodeIssuedAt(now); value.setCodeExpiresAt(now.plusSeconds(600));
        event(order, value, actor, "RETURN_CODE_ISSUED", order.getStatus(), order.getStatus(), "Người gửi tạo mã nhận lại hàng, hiệu lực 10 phút.");
        return new ReturnResponse.Code(code, value.getCodeExpiresAt());
    }

    @Transactional(noRollbackFor = ReturnCodeRejectedException.class)
    public ReturnResponse confirm(UUID actorId, Long orderId, ReturnRequests.Confirm input) {
        User actor = access.read(actorId);
        Order order = lockOrder(orderId);
        requireSender(actor, order);
        requireStatus(order, OrderStatus.RETURNING);
        if (input == null || !Boolean.TRUE.equals(input.received()) || input.code() == null || !input.code().matches("[0-9]{6}")) invalid();
        var value = activeCase(order);
        if (value.getCodeFailures() >= 5) throw new AppException(RETURN_CODE_LOCKED);
        if (value.getCodeHash() == null || value.getCodeExpiresAt() == null || !clock.instant().isBefore(value.getCodeExpiresAt()))
            throw new AppException(RETURN_CODE_EXPIRED);
        if (!MessageDigest.isEqual(value.getCodeHash().getBytes(StandardCharsets.UTF_8), hash(value.getCodeSalt(), input.code()).getBytes(StandardCharsets.UTF_8))) {
            value.setCodeFailures(value.getCodeFailures() + 1);
            throw new ReturnCodeRejectedException();
        }
        value.setState(ReturnState.RETURNED); value.setResolvedAt(clock.instant()); invalidateCode(value);
        order.setStatus(OrderStatus.RETURNED); order.setEndedAt(clock.instant());
        var milestone = event(order, value, actor, "RETURN_CONFIRMED", OrderStatus.RETURNING, OrderStatus.RETURNED,
                "Người gửi xác nhận đã nhận lại hàng. Phí đang chờ xử lý tài chính, chưa tất toán tự động.");
        orders.save(order);
        notifyParties(order, milestone, "Người gửi đã xác nhận nhận lại hàng");
        return response(order, actor, policy());
    }

    @Transactional(readOnly = true)
    public ValidatedImage evidence(UUID actorId, Long orderId, Long caseId) {
        User actor = access.read(actorId); Order order = readOrder(orderId); requireReader(actor, order);
        var value = cases.findByIdAndOrderId(caseId, orderId).orElseThrow(() -> new AppException(RETURN_NOT_FOUND));
        return new ValidatedImage(storage.read(value.getEvidenceKey()), value.getEvidenceMediaType());
    }

    private ReturnResponse response(Order order, User actor, Policy policy) {
        var latest = cases.findTopByOrderIdOrderByIdDesc(order.getId()).map(ReturnResponse.Case::from).orElse(null);
        return new ReturnResponse(order.getId(), order.getStatus(), order.getStatus() == OrderStatus.IN_DELIVERY
                && role(actor, "COURIER") && Objects.equals(actor.getId(), order.getCourierId()),
                order.getStatus() == OrderStatus.RETURNING && role(actor, "SENDER") && Objects.equals(actor.getId(), order.getSenderId()),
                order.getStatus() == OrderStatus.RETURNING && role(actor, "COURIER") && Objects.equals(actor.getId(), order.getCourierId()),
                policy.calls(), policy.minutes(), unusedContacts(order, policy).stream().map(ReturnResponse.Contact::from).toList(), latest,
                history.findTop50ByOrderIdOrderByIdDesc(order.getId()).stream().map(ReturnResponse.History::from).toList());
    }
    private List<DeliveryContactAttempt> unusedContacts(Order order, Policy policy) {
        return contacts.findByOrderIdAndCourierIdAndReturnCaseIsNullAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(
                order.getId(), order.getCourierId(), clock.instant().minusSeconds(policy.minutes() * 60L));
    }
    private DeliveryReturnCase activeCase(Order order) {
        var value = cases.findTopByOrderIdOrderByIdDesc(order.getId()).orElseThrow(() -> new AppException(RETURN_NOT_FOUND));
        if (value.getState() != ReturnState.RETURNING || !Objects.equals(value.getCourierId(), order.getCourierId()))
            throw new AppException(RETURN_INVALID_STATUS);
        return value;
    }
    private DeliveryReturnHistory event(Order order, DeliveryReturnCase value, User actor, String action, OrderStatus from, OrderStatus to, String note) {
        return history.save(DeliveryReturnHistory.builder().orderId(order.getId()).returnCaseId(value == null ? null : value.getId())
                .actorId(actor.getId()).action(action).fromStatus(from).toStatus(to).note(note).createdAt(clock.instant()).build());
    }
    private void notifyParties(Order order, DeliveryReturnHistory event, String title) {
        Set<UUID> targets = new TreeSet<>();
        for (Long id : Set.of(order.getSenderId(), order.getCourierId())) users.findById(id)
                .filter(u -> !u.isDeleted() && u.getStatus() != UserStatus.DELETED).map(User::getPublicId).ifPresent(targets::add);
        for (UUID target : targets) notifications.publish(target, NotificationCategory.ORDER, "RETURN:" + event.getId(), title,
                "Đơn " + order.getOrderCode() + ": " + event.getNote(), "/orders/" + order.getId() + "/return");
    }
    private record Policy(int calls, int minutes, BigDecimal rate, long feeVersion) {}
    private Policy policy() {
        try {
            var snapshot = parameters.getSnapshot();
            var match = Pattern.compile("(\\d+)\\D+(\\d+)").matcher(snapshot.value("CONTACT_TRIES"));
            if (!match.find()) throw new IllegalArgumentException();
            int calls = Integer.parseInt(match.group(1)), minutes = Integer.parseInt(match.group(2));
            BigDecimal rate = snapshot.decimal("RETURN_FEE");
            if (calls < 1 || minutes < 1 || rate.signum() < 0 || rate.compareTo(BigDecimal.ONE) > 0) throw new IllegalArgumentException();
            return new Policy(calls, minutes, rate, snapshot.versions().get("RETURN_FEE"));
        } catch (IllegalArgumentException | NullPointerException exception) { throw new AppException(RETURN_POLICY_UNAVAILABLE); }
    }
    private Order lockOrder(Long id) { return orders.findByIdForUpdate(id).orElseThrow(() -> new AppException(OrderErrorCode.ORDER_NOT_FOUND)); }
    private Order readOrder(Long id) { return orders.findById(id).orElseThrow(() -> new AppException(OrderErrorCode.ORDER_NOT_FOUND)); }
    private boolean role(User actor, String role) { return actor.getRoles().stream().anyMatch(r -> role.equals(r.getCode())); }
    private void requireCourier(User actor, Order order) {
        if (!role(actor, "COURIER") || !Objects.equals(actor.getId(), order.getCourierId())) denied();
    }
    private void requireSender(User actor, Order order) {
        if (!role(actor, "SENDER") || !Objects.equals(actor.getId(), order.getSenderId())) denied();
    }
    private void requireReader(User actor, Order order) {
        if (!Objects.equals(actor.getId(), order.getSenderId()) && !Objects.equals(actor.getId(), order.getCourierId())
                && !role(actor, "MODERATOR") && !role(actor, "ADMIN")) denied();
    }
    private void requireStatus(Order order, OrderStatus status) { if (order.getStatus() != status) throw new AppException(RETURN_INVALID_STATUS); }
    private void text(String value, int max) { if (value == null || value.isBlank() || value.length() > max) invalid(); }
    private void invalid() { throw new AppException(GlobalErrorCode.INVALID_REQUEST); }
    private void denied() { throw new AppException(RETURN_ACCESS_DENIED); }
    private void invalidateCode(DeliveryReturnCase value) { value.setCodeHash(null); value.setCodeSalt(null); value.setCodeExpiresAt(null); }
    private String hash(String salt, String code) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((salt + ":" + code).getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 unavailable", exception); }
    }
}
