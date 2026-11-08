package com.fptpost.modules.moderation.service;

import com.fptpost.common.audit.AuditOutboxService;
import com.fptpost.common.exception.AppException;
import com.fptpost.common.response.PageResponse;
import com.fptpost.modules.catalog.model.ItemCategory;
import com.fptpost.modules.catalog.repository.ItemCategoryRepository;
import com.fptpost.modules.identity.dto.response.UserAccountEntry;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.repository.UserRepository;
import com.fptpost.modules.identity.service.IdentityUserAccess;
import com.fptpost.modules.identity.service.NotificationRecipientLookup;
import com.fptpost.modules.identity.service.UserDirectoryLookup;
import com.fptpost.modules.moderation.dto.request.OrderModerationRejectRequest;
import com.fptpost.modules.moderation.dto.request.OrderModerationSearchRequest;
import com.fptpost.modules.moderation.dto.response.ModerationPartyResponse;
import com.fptpost.modules.moderation.dto.response.OrderModerationOutcomeResponse;
import com.fptpost.modules.moderation.dto.response.OrderModerationResponse;
import com.fptpost.modules.moderation.enums.OrderModerationStatus;
import com.fptpost.modules.moderation.exception.ModerationErrorCode;
import com.fptpost.modules.moderation.model.OrderModeration;
import com.fptpost.modules.moderation.repository.ModerationKeywordRepository;
import com.fptpost.modules.moderation.repository.OrderModerationRepository;
import com.fptpost.modules.notification.enums.NotificationCategory;
import com.fptpost.modules.notification.service.NotificationPublisher;
import com.fptpost.modules.order.exception.OrderErrorCode;
import com.fptpost.modules.order.model.Order;
import com.fptpost.modules.order.model.OrderImage;
import com.fptpost.modules.order.model.OrderStatus;
import com.fptpost.modules.order.repository.OrderRepository;
import com.fptpost.modules.order.service.OrderSubmissionScreening;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** UC-54: kiểm duyệt tự động khi đăng/sửa đơn và hàng đợi kiểm duyệt thủ công. */
@Service
@RequiredArgsConstructor
public class OrderModerationService implements OrderSubmissionScreening {

    static final String CATEGORY_PROHIBITED = "PROHIBITED";
    static final String CATEGORY_REQUIRES_REVIEW = "REQUIRES_REVIEW";

    private final OrderModerationRepository moderations;
    private final ModerationKeywordRepository keywords;
    private final OrderContentScanner scanner;
    private final OrderRepository orderRepository;
    private final ItemCategoryRepository categories;
    private final UserRepository userRepository;
    private final IdentityUserAccess userAccess;
    private final UserDirectoryLookup directory;
    private final NotificationRecipientLookup recipients;
    private final NotificationPublisher notifications;
    private final AuditOutboxService audit;
    private final Clock clock;

    @Value("${app.moderation.repeat-rejection-threshold:3}")
    private int repeatRejectionThreshold;

    @Value("${app.moderation.repeat-rejection-window-days:30}")
    private int repeatRejectionWindowDays;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void screen(Order order) {
        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.FINDING_SHIPPER) return;
        String hash = contentHash(order);
        OrderModeration moderation = moderations.findByOrderId(order.getId()).orElse(null);
        if (moderation != null && moderation.getStatus() == OrderModerationStatus.APPROVED && hash.equals(moderation.getContentHash())) {
            order.setStatus(OrderStatus.FINDING_SHIPPER);
            return;
        }

        List<String> terms = scanner.matches(content(order), keywords.findAll());
        String categoryFlag = categoryFlag(order.getCategoryId());
        boolean clean = terms.isEmpty() && categoryFlag == null;
        if (moderation == null) {
            moderation = OrderModeration.builder().orderId(order.getId()).senderId(order.getSenderId()).build();
        }
        Instant now = clock.instant();
        moderation.setStatus(clean ? OrderModerationStatus.AUTO_APPROVED : OrderModerationStatus.PENDING);
        moderation.setMatchedTerms(clean ? null : joinTerms(terms));
        moderation.setCategoryFlag(categoryFlag);
        moderation.setContentHash(hash);
        moderation.setSubmittedAt(now);
        moderation.setReviewerId(null);
        moderation.setReviewedAt(null);
        moderation.setRejectionReason(null);
        moderations.save(moderation);
        order.setStatus(clean ? OrderStatus.FINDING_SHIPPER : OrderStatus.PENDING);

        if (!clean) {
            notifySender(order, "order-moderation:" + order.getId() + ":pending:" + now.toEpochMilli(),
                    "Đơn hàng đang chờ kiểm duyệt",
                    "Đơn " + order.getOrderCode() + " cần kiểm duyệt viên xem xét trước khi hiển thị trên sàn.");
        }
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    public PageResponse<OrderModerationResponse> pending(UUID actorPublicId, OrderModerationSearchRequest request) {
        User moderator = userAccess.read(actorPublicId);
        int page = request.getPage() == null ? 0 : request.getPage();
        int size = request.getSize() == null ? 20 : request.getSize();
        Page<OrderModeration> result = moderations.search(OrderModerationStatus.PENDING, pattern(request.getQ()),
                PageRequest.of(page, size));
        View view = view(moderator, result.getContent());
        return PageResponse.from(result.map(item -> response(item, view)));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    public OrderModerationResponse detail(UUID actorPublicId, Long orderId) {
        User moderator = userAccess.read(actorPublicId);
        OrderModeration moderation = moderations.findByOrderId(orderId)
                .orElseThrow(() -> new AppException(ModerationErrorCode.ORDER_MODERATION_NOT_FOUND));
        return response(moderation, view(moderator, List.of(moderation)));
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    public OrderModerationResponse approve(UUID actorPublicId, Long orderId) {
        User moderator = userAccess.read(actorPublicId);
        Decision decision = lockDecision(orderId, moderator);
        Map<String, Object> before = state(decision);
        Instant now = clock.instant();
        decision.moderation().setStatus(OrderModerationStatus.APPROVED);
        decision.moderation().setReviewerId(moderator.getId());
        decision.moderation().setReviewedAt(now);
        decision.order().setStatus(OrderStatus.FINDING_SHIPPER);
        moderations.saveAndFlush(decision.moderation());
        orderRepository.saveAndFlush(decision.order());
        audit.enqueue("ORDER_MODERATION_APPROVED", "ORDER", decision.order().getOrderCode(), before, state(decision));
        notifySender(decision.order(), "order-moderation:" + orderId + ":approved:" + now.toEpochMilli(),
                "Đơn hàng đã được duyệt",
                "Đơn " + decision.order().getOrderCode() + " đã qua kiểm duyệt và hiển thị trên sàn cho người giao.");
        return response(decision.moderation(), view(moderator, List.of(decision.moderation())));
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    public OrderModerationResponse reject(UUID actorPublicId, Long orderId, OrderModerationRejectRequest input) {
        User moderator = userAccess.read(actorPublicId);
        Decision decision = lockDecision(orderId, moderator);
        Map<String, Object> before = state(decision);
        Instant now = clock.instant();
        String reason = input.getReason().strip();
        decision.moderation().setStatus(OrderModerationStatus.REJECTED);
        decision.moderation().setRejectionReason(reason);
        decision.moderation().setReviewerId(moderator.getId());
        decision.moderation().setReviewedAt(now);
        decision.order().setStatus(OrderStatus.REJECTED);
        decision.order().setEndedAt(now);
        moderations.saveAndFlush(decision.moderation());
        orderRepository.saveAndFlush(decision.order());
        audit.enqueue("ORDER_MODERATION_REJECTED", "ORDER", decision.order().getOrderCode(), before, state(decision));
        notifySender(decision.order(), "order-moderation:" + orderId + ":rejected:" + now.toEpochMilli(),
                "Đơn hàng không được duyệt",
                "Đơn " + decision.order().getOrderCode() + " không được đăng lên sàn. Lý do: " + reason);
        warnRepeatedRejections(decision.order(), now);
        return response(decision.moderation(), view(moderator, List.of(decision.moderation())));
    }

    @Transactional(readOnly = true)
    public OrderModerationOutcomeResponse outcome(UUID actorPublicId, Long orderId) {
        User sender = userAccess.read(actorPublicId);
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new AppException(OrderErrorCode.ORDER_NOT_FOUND));
        if (!sender.getId().equals(order.getSenderId())) {
            throw new AppException(ModerationErrorCode.ORDER_MODERATION_ACCESS_DENIED);
        }
        OrderModeration moderation = moderations.findByOrderId(orderId)
                .orElseThrow(() -> new AppException(ModerationErrorCode.ORDER_MODERATION_NOT_FOUND));
        return OrderModerationOutcomeResponse.builder()
                .status(moderation.getStatus())
                .submittedAt(moderation.getSubmittedAt())
                .reviewedAt(moderation.getReviewedAt())
                .rejectionReason(moderation.getRejectionReason())
                .build();
    }

    private Decision lockDecision(Long orderId, User moderator) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new AppException(ModerationErrorCode.ORDER_MODERATION_NOT_FOUND));
        OrderModeration moderation = moderations.findByOrderId(orderId)
                .orElseThrow(() -> new AppException(ModerationErrorCode.ORDER_MODERATION_NOT_FOUND));
        if (moderator.getId().equals(order.getSenderId())) {
            throw new AppException(ModerationErrorCode.ORDER_MODERATION_SELF_FORBIDDEN);
        }
        if (moderation.getStatus() != OrderModerationStatus.PENDING || order.getStatus() != OrderStatus.PENDING) {
            throw new AppException(ModerationErrorCode.ORDER_MODERATION_ALREADY_DECIDED);
        }
        return new Decision(order, moderation);
    }

    /** BR-F-107 A1: người gửi bị từ chối nhiều lần trong cửa sổ cảnh báo nhận thêm cảnh báo vi phạm. */
    private void warnRepeatedRejections(Order order, Instant now) {
        Instant since = now.minus(Duration.ofDays(repeatRejectionWindowDays));
        long count = moderations.countBySenderIdAndStatusAndReviewedAtGreaterThanEqual(order.getSenderId(),
                OrderModerationStatus.REJECTED, since);
        if (count < repeatRejectionThreshold) return;
        notifySender(order, "order-moderation:repeat:" + order.getSenderId() + ":" + now.toEpochMilli(),
                "Cảnh báo vi phạm khi đăng đơn",
                "Bạn có " + count + " đơn bị từ chối trong " + repeatRejectionWindowDays
                        + " ngày gần đây. Vi phạm tiếp có thể dẫn đến hạn chế hoặc khóa tài khoản.");
    }

    private void notifySender(Order order, String eventKey, String title, String body) {
        if (order.getSenderId() == null) return;
        UUID recipient = userRepository.findById(order.getSenderId()).map(User::getPublicId).orElse(null);
        if (recipient == null) return;
        boolean reachable = recipients.find(recipient).map(NotificationRecipientLookup.Recipient::canReceive).orElse(false);
        if (!reachable) return;
        notifications.publish(recipient, NotificationCategory.ORDER, eventKey, title, body, "/sender/orders/" + order.getId());
    }

    private String categoryFlag(Long categoryId) {
        if (categoryId == null) return null;
        ItemCategory category = categories.findById(categoryId).orElse(null);
        if (category == null) return null;
        if (Boolean.TRUE.equals(category.getIsProhibited())) return CATEGORY_PROHIBITED;
        if (Boolean.TRUE.equals(category.getRequiresReview())) return CATEGORY_REQUIRES_REVIEW;
        return null;
    }

    private static String content(Order order) {
        return Stream.of(order.getProductName(), order.getProductDescription(), order.getDeliveryNote())
                .filter(Objects::nonNull).collect(Collectors.joining("\n"));
    }

    private static String contentHash(Order order) {
        String images = order.getImages() == null ? "" : order.getImages().stream()
                .map(OrderImage::getImageUrl).collect(Collectors.joining("|"));
        String value = String.join("\u0000", OrderContentScanner.normalize(order.getProductName()),
                OrderContentScanner.normalize(order.getProductDescription()),
                OrderContentScanner.normalize(order.getDeliveryNote()),
                String.valueOf(order.getCategoryId()), images);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String joinTerms(List<String> terms) {
        StringBuilder joined = new StringBuilder();
        for (String term : terms) {
            if (joined.length() + term.length() + 2 > 500) break;
            if (!joined.isEmpty()) joined.append(", ");
            joined.append(term);
        }
        return joined.toString();
    }

    private static String pattern(String query) {
        if (query == null || query.isBlank()) return null;
        return "%" + query.trim().toLowerCase(Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_").replace("[", "![") + "%";
    }

    private View view(User moderator, List<OrderModeration> items) {
        Set<Long> orderIds = items.stream().map(OrderModeration::getOrderId).collect(Collectors.toSet());
        Map<Long, Order> orders = orderRepository.findAllById(orderIds).stream()
                .collect(Collectors.toMap(Order::getId, Function.identity()));
        Set<Long> categoryIds = orders.values().stream().map(Order::getCategoryId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> categoryNames = categories.findAllById(categoryIds).stream()
                .collect(Collectors.toMap(ItemCategory::getId, ItemCategory::getName));
        Set<Long> people = new HashSet<>();
        items.forEach(item -> {
            people.add(item.getSenderId());
            if (item.getReviewerId() != null) people.add(item.getReviewerId());
        });
        Set<Long> senders = items.stream().map(OrderModeration::getSenderId).collect(Collectors.toSet());
        Map<Long, Long> rejections = new HashMap<>();
        if (!senders.isEmpty()) {
            Instant since = clock.instant().minus(Duration.ofDays(repeatRejectionWindowDays));
            for (Object[] row : moderations.countBySenders(senders, OrderModerationStatus.REJECTED, since)) {
                rejections.put((Long) row[0], (Long) row[1]);
            }
        }
        return new View(moderator.getId(), orders, categoryNames, directory.accounts(people), rejections);
    }

    private OrderModerationResponse response(OrderModeration item, View view) {
        Order order = view.orders().get(item.getOrderId());
        boolean canDecide = item.getStatus() == OrderModerationStatus.PENDING && order != null
                && order.getStatus() == OrderStatus.PENDING && !view.actorId().equals(item.getSenderId());
        return OrderModerationResponse.builder()
                .orderId(item.getOrderId())
                .orderCode(order == null ? null : order.getOrderCode())
                .status(item.getStatus())
                .orderStatus(order == null ? null : order.getStatus())
                .productName(order == null ? null : order.getProductName())
                .productDescription(order == null ? null : order.getProductDescription())
                .deliveryNote(order == null ? null : order.getDeliveryNote())
                .quantity(order == null ? null : order.getQuantity())
                .weightKg(order == null ? null : order.getWeightKg())
                .declaredValue(order == null ? null : order.getDeclaredValue())
                .categoryName(order == null || order.getCategoryId() == null ? null : view.categoryNames().get(order.getCategoryId()))
                .categoryFlag(item.getCategoryFlag())
                .matchedTerms(item.getMatchedTerms() == null || item.getMatchedTerms().isBlank() ? List.of()
                        : Arrays.stream(item.getMatchedTerms().split(", ")).toList())
                .imageUrls(order == null || order.getImages() == null ? List.of()
                        : order.getImages().stream().map(OrderImage::getImageUrl).toList())
                .sender(party(view, item.getSenderId()))
                .senderRecentRejections(view.rejections().getOrDefault(item.getSenderId(), 0L))
                .submittedAt(item.getSubmittedAt())
                .reviewer(party(view, item.getReviewerId()))
                .reviewedAt(item.getReviewedAt())
                .rejectionReason(item.getRejectionReason())
                .canDecide(canDecide)
                .build();
    }

    private static ModerationPartyResponse party(View view, Long userId) {
        if (userId == null) return null;
        UserAccountEntry account = view.accounts().get(userId);
        if (account == null) return null;
        return ModerationPartyResponse.builder().publicId(account.publicId()).fullName(account.fullName())
                .email(account.email()).build();
    }

    private static Map<String, Object> state(Decision decision) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("orderStatus", decision.order().getStatus().name());
        state.put("moderationStatus", decision.moderation().getStatus().name());
        if (decision.moderation().getMatchedTerms() != null) state.put("matchedTerms", decision.moderation().getMatchedTerms());
        if (decision.moderation().getRejectionReason() != null) state.put("reason", decision.moderation().getRejectionReason());
        return state;
    }

    private record Decision(Order order, OrderModeration moderation) {
    }

    private record View(Long actorId, Map<Long, Order> orders, Map<Long, String> categoryNames,
                        Map<Long, UserAccountEntry> accounts, Map<Long, Long> rejections) {
    }
}
