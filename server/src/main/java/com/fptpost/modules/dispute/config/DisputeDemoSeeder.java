package com.fptpost.modules.dispute.config;

import com.fptpost.common.file.dto.ValidatedImage;
import com.fptpost.modules.chat.model.ChatMessage;
import com.fptpost.modules.chat.model.ChatRoom;
import com.fptpost.modules.chat.repository.ChatMessageRepository;
import com.fptpost.modules.chat.repository.ChatRoomRepository;
import com.fptpost.modules.dispute.dto.request.OpenComplaintCommand;
import com.fptpost.modules.dispute.enums.ComplaintOutcome;
import com.fptpost.modules.dispute.enums.ComplaintReason;
import com.fptpost.modules.dispute.enums.ComplaintStatus;
import com.fptpost.modules.dispute.model.Complaint;
import com.fptpost.modules.dispute.service.ComplaintLifecycle;
import com.fptpost.modules.order.model.Order;
import com.fptpost.modules.order.model.OrderStatus;
import com.fptpost.modules.order.repository.OrderRepository;
import com.fptpost.modules.payment.model.Escrow;
import com.fptpost.modules.payment.model.EscrowPaymentMethod;
import com.fptpost.modules.payment.model.EscrowStatus;
import com.fptpost.modules.payment.repository.EscrowRepository;
import com.fptpost.modules.payment.service.LedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Dữ liệu demo cho khiếu nại, bồi thường và điểm uy tín khi luồng giao hàng (UC-28) và tạo khiếu nại (UC-50) chưa có.
 * Chạy sau khi ứng dụng sẵn sàng, chỉ ở profile dev/demo/local, và chỉ bổ sung phần còn thiếu nên khởi động lại không tạo trùng:
 * <ul>
 *   <li>6 đơn đã hoàn tất của người giao demo, để điểm uy tín có dữ liệu;</li>
 *   <li>một khiếu nại đang mở trên đơn có bảo hiểm, kèm chat của đơn, để chạy trọn luồng tiếp nhận → phán quyết → kháng nghị
 *   → đóng hồ sơ;</li>
 *   <li>một khiếu nại đã đóng có đề nghị bồi thường, để Quỹ bảo đảm có khoản chờ duyệt.</li>
 * </ul>
 */
@Slf4j
@Component
@Profile({"dev", "demo", "local"})
@ConditionalOnProperty(name = "app.demo.complaints-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class DisputeDemoSeeder {

    static final String SENDER = "sender@fptpost.vn";
    static final String COURIER = "courier@fptpost.vn";
    static final String MODERATOR = "moderator@fptpost.vn";
    private static final int COMPLETED_HISTORY = 6;

    private final DemoAccountLookup lookup;
    private final OrderRepository orders;
    private final EscrowRepository escrows;
    private final LedgerService ledgerService;
    private final ComplaintLifecycle lifecycle;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;
    private final ChatRoomRepository chatRooms;
    private final ChatMessageRepository chatMessages;

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        Order disputed;
        try {
            disputed = new TransactionTemplate(transactionManager).execute(status -> seedMissing());
        } catch (RuntimeException exception) {
            log.warn("Complaint demo data was not created", exception);
            return;
        }
        if (disputed != null) seedChat(disputed);
    }

    /** Bổ sung phần dữ liệu còn thiếu; trả về đơn vừa mở khiếu nại để tạo chat mẫu, hoặc {@code null}. */
    Order seedMissing() {
        Long sender = lookup.idByEmail(SENDER).orElse(null);
        Long courier = lookup.idByEmail(COURIER).orElse(null);
        Long moderator = lookup.idByEmail(MODERATOR).orElse(null);
        if (sender == null || courier == null) return null;
        Instant now = clock.instant();
        Order disputed = null;

        for (long done = lookup.countOrders("DEMO-HT-%"); done < COMPLETED_HISTORY; done++) {
            Order order = order("DEMO-HT-" + (done + 1), sender, courier, OrderStatus.COMPLETED, "Tài liệu hồ sơ " + (done + 1),
                    BigDecimal.valueOf(200_000), now.minus(Duration.ofDays(20 - done)));
            order.setEndedAt(now.minus(Duration.ofDays(19 - done)));
        }

        if (lookup.countActiveComplaints("DEMO-KN-%") == 0) {
            Order order = order("DEMO-KN-" + suffix(), sender, courier, OrderStatus.IN_DELIVERY, "Tai nghe không dây",
                    BigDecimal.valueOf(1_500_000), now.minus(Duration.ofHours(30)));
            escrow(order, sender, 120_000L, now);
            lifecycle.open(new OpenComplaintCommand(order.getId(), sender, ComplaintReason.DAMAGED,
                    "Hộp móp một góc, một bên tai nghe không lên tiếng khi nhận hàng.", List.of(evidence())));
            log.info("Created demo complaint on order {}", order.getOrderCode());
            disputed = order;
        }

        if (lookup.countOrders("DEMO-BT-%") == 0) {
            Order order = order("DEMO-BT-01", sender, courier, OrderStatus.IN_DELIVERY, "Đồng hồ thông minh",
                    BigDecimal.valueOf(2_000_000), now.minus(Duration.ofDays(5)));
            escrow(order, sender, 90_000L, now);
            Complaint complaint = lifecycle.open(new OpenComplaintCommand(order.getId(), sender, ComplaintReason.LOST,
                    "Đơn báo đã giao nhưng tôi không nhận được hàng.", List.of(evidence())));
            complaint.setAssignedTo(moderator);
            complaint.setFirstResponseAt(now.minus(Duration.ofDays(3)));
            complaint.setOutcome(ComplaintOutcome.SENDER_FAVOR);
            complaint.setRefundAmount(90_000L);
            complaint.setCourierAmount(0L);
            complaint.setCompensationAmount(400_000L);
            complaint.setRulingReason("Không có xác nhận giao hàng; hàng có bảo hiểm nên đề nghị bồi thường một phần giá trị khai báo.");
            complaint.setRuledBy(moderator);
            complaint.setRuledAt(now.minus(Duration.ofDays(3)));
            complaint.setAppealDeadline(now.minus(Duration.ofDays(1)));
            complaint.setStatus(ComplaintStatus.RESOLVED);
            lifecycle.close(complaint, now);
            log.info("Created closed demo complaint with compensation on order {}", order.getOrderCode());
        }
        return disputed;
    }

    /** Chat của đơn đang khiếu nại, để kiểm duyệt viên có lịch sử đối chiếu. Kho chat lỗi thì bỏ qua, không ảnh hưởng hồ sơ. */
    private void seedChat(Order order) {
        Instant pickedUp = order.getCreatedAt().plus(Duration.ofHours(2));
        try {
            ChatRoom room = chatRooms.save(ChatRoom.builder().orderId(order.getId()).senderId(order.getSenderId())
                    .courierId(order.getCourierId()).createdAt(pickedUp).lastMessageAt(pickedUp.plus(Duration.ofHours(4)))
                    .build());
            chatMessages.saveAll(List.of(
                    chat(room, order.getCourierId(), 1, "Mình đã lấy hàng, khoảng 2 tiếng nữa giao tới nhé.", pickedUp),
                    chat(room, order.getSenderId(), 2, "Ok bạn, tai nghe dễ vỡ nên nhẹ tay giúp mình.",
                            pickedUp.plus(Duration.ofMinutes(10))),
                    chat(room, order.getCourierId(), 3, "Mình giao xong rồi, người nhận đã nhận hàng.",
                            pickedUp.plus(Duration.ofHours(2))),
                    chat(room, order.getSenderId(), 4, "Người nhận báo hộp móp một góc và một bên tai nghe không lên tiếng.",
                            pickedUp.plus(Duration.ofHours(4)))));
        } catch (RuntimeException exception) {
            log.warn("Demo chat for order {} was not created", order.getOrderCode());
        }
    }

    private static ChatMessage chat(ChatRoom room, Long author, int sequence, String content, Instant createdAt) {
        return ChatMessage.builder().roomId(room.getId()).authorId(author).clientMessageId("demo-" + sequence)
                .content(content).createdAt(createdAt).build();
    }

    private Order order(String code, Long sender, Long courier, OrderStatus status, String product, BigDecimal declared,
                        Instant createdAt) {
        return orders.saveAndFlush(Order.builder().orderCode(code).senderId(sender).courierId(courier).status(status)
                .pickupAddress("Tòa Alpha, Đại học FPT, Hòa Lạc").deliveryAddress("Số 8 Tôn Thất Thuyết, Cầu Giấy, Hà Nội")
                .receiverName("Nguyễn Văn Nhận").receiverPhone("0912345678").productName(product).quantity(1)
                .weightKg(BigDecimal.ONE).declaredValue(declared).priceSuggested(BigDecimal.valueOf(45_000))
                .priceAgreed(BigDecimal.valueOf(45_000)).createdAt(createdAt).updatedAt(createdAt).build());
    }

    private void escrow(Order order, Long sender, long amount, Instant now) {
        escrows.saveAndFlush(Escrow.builder().orderId(order.getId()).senderId(sender).amount(amount).status(EscrowStatus.HELD)
                .paymentMethod(EscrowPaymentMethod.VNPAY).releaseAt(now.plus(Duration.ofHours(24))).frozen(false).build());
        ledgerService.recordOrderEscrowFromVNPay(order.getId(), sender, amount, "DEMO_" + order.getOrderCode());
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
    }

    /** Ảnh bằng chứng minh họa: khung hộp hàng với vết móp. */
    private static ValidatedImage evidence() {
        BufferedImage image = new BufferedImage(240, 180, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(0xF4E7D3));
        graphics.fillRect(0, 0, 240, 180);
        graphics.setColor(new Color(0xB07D48));
        graphics.fillRect(40, 40, 160, 100);
        graphics.setColor(new Color(0x6B4423));
        graphics.fillPolygon(new int[]{170, 200, 200}, new int[]{40, 40, 75}, 3);
        graphics.dispose();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return new ValidatedImage(out.toByteArray(), "image/png");
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
