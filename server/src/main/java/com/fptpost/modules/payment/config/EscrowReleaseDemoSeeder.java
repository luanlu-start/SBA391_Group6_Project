package com.fptpost.modules.payment.config;

import com.fptpost.modules.identity.repository.UserRepository;
import com.fptpost.modules.order.model.Order;
import com.fptpost.modules.order.model.OrderStatus;
import com.fptpost.modules.order.repository.OrderRepository;
import com.fptpost.modules.payment.model.Escrow;
import com.fptpost.modules.payment.model.EscrowPaymentMethod;
import com.fptpost.modules.payment.model.EscrowStatus;
import com.fptpost.modules.payment.model.Wallet;
import com.fptpost.modules.payment.repository.EscrowRepository;
import com.fptpost.modules.payment.repository.WalletRepository;
import com.fptpost.modules.payment.service.LedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/**
 * Tạo sẵn 1 đơn hàng mẫu đã giao thành công (COMPLETED) kèm ký quỹ HELD
 * trên môi trường dev/demo/local để kiểm thử luồng giải ngân (UC-36) & thu nhập người giao (UC-61).
 */
@Slf4j
@Component
@org.springframework.core.annotation.Order(3)
@Profile({"dev", "demo", "local"})
@RequiredArgsConstructor
public class EscrowReleaseDemoSeeder implements CommandLineRunner {

    private static final String DEMO_DELIVERED_ORDER_CODE = "ORD-TEST-DELIVERED";
    private static final long DEMO_ESCROW_AMOUNT = 300_000L;

    private final Optional<OrderRepository> orderRepository;
    private final Optional<UserRepository> userRepository;
    private final EscrowRepository escrowRepository;
    private final WalletRepository walletRepository;
    private final LedgerService ledgerService;

    @Override
    @Transactional
    public void run(String... args) {
        if (orderRepository.isEmpty() || userRepository.isEmpty()) {
            return;
        }
        OrderRepository orders = orderRepository.get();
        if (orders.existsByOrderCode(DEMO_DELIVERED_ORDER_CODE)) {
            return;
        }

        Optional<Long> senderIdOpt = userRepository.get().findIdByIdentifier("sender@fptpost.vn");
        Optional<Long> courierIdOpt = userRepository.get().findIdByIdentifier("courier@fptpost.vn");
        if (senderIdOpt.isEmpty() || courierIdOpt.isEmpty()) {
            return;
        }

        Long senderId = senderIdOpt.get();
        Long courierId = courierIdOpt.get();
        Instant now = Instant.now();

        Order deliveredOrder = Order.builder()
                .orderCode(DEMO_DELIVERED_ORDER_CODE)
                .senderId(senderId)
                .courierId(courierId)
                .pickupAddress("Khu Công Nghệ Cao Hòa Lạc, Thạch Thất, Hà Nội")
                .deliveryAddress("Tòa nhà FPT, Cầu Giấy, Hà Nội")
                .receiverName("Lê Thị Đã Nhận")
                .receiverPhone("0912345678")
                .productName("Đơn hàng mẫu đã giao xong (Chờ giải ngân UC-36)")
                .quantity(1)
                .weightKg(BigDecimal.valueOf(1.5))
                .declaredValue(BigDecimal.valueOf(DEMO_ESCROW_AMOUNT))
                .priceSuggested(BigDecimal.valueOf(DEMO_ESCROW_AMOUNT))
                .priceAgreed(BigDecimal.valueOf(DEMO_ESCROW_AMOUNT))
                .deliveryNote("Đơn mẫu COMPLETED phục vụ kiểm thử giải ngân và thu nhập Courier")
                .status(OrderStatus.COMPLETED)
                .createdAt(now.minusSeconds(5 * 3600))
                .updatedAt(now.minusSeconds(3 * 3600))
                .endedAt(now.minusSeconds(3 * 3600))
                .build();

        Order savedOrder = orders.save(deliveredOrder);

        if (!escrowRepository.existsByOrderId(savedOrder.getId())) {
            escrowRepository.save(Escrow.builder()
                    .orderId(savedOrder.getId())
                    .senderId(senderId)
                    .amount(DEMO_ESCROW_AMOUNT)
                    .status(EscrowStatus.HELD)
                    .paymentMethod(EscrowPaymentMethod.WALLET)
                    .frozen(false)
                    .build());

            Wallet senderWallet = walletRepository.findByUserIdForUpdate(senderId).orElseGet(() ->
                    walletRepository.save(Wallet.builder()
                            .userId(senderId)
                            .available(2_500_000L)
                            .escrowHeld(0L)
                            .pendingWithdraw(0L)
                            .build()));
            long currentHeld = senderWallet.getEscrowHeld() != null ? senderWallet.getEscrowHeld() : 0L;
            senderWallet.setEscrowHeld(currentHeld + DEMO_ESCROW_AMOUNT);
            walletRepository.save(senderWallet);

            ledgerService.recordOrderEscrowFromWallet(
                    savedOrder.getId(),
                    senderId,
                    DEMO_ESCROW_AMOUNT,
                    "ESCROW_DEMO_DELIVERED_" + savedOrder.getId()
            );

            log.info("Seeded COMPLETED sample order {} (id={}, escrow={} VND) ready for UC-36 release test",
                    DEMO_DELIVERED_ORDER_CODE, savedOrder.getId(), DEMO_ESCROW_AMOUNT);
        }
    }
}
