package com.fptpost.modules.payment.config;

import com.fptpost.modules.identity.config.UserDemoSeeder;
import com.fptpost.modules.identity.repository.UserRepository;
import com.fptpost.modules.order.model.Order;
import com.fptpost.modules.order.model.OrderStatus;
import com.fptpost.modules.order.repository.OrderRepository;
import com.fptpost.modules.payment.model.PaymentStatus;
import com.fptpost.modules.payment.model.PaymentTransaction;
import com.fptpost.modules.payment.repository.PaymentTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/**
 * Automatically seeds sample orders, payment transactions, and links them to demo users for local/dev/test environments.
 */
@Slf4j
@Component
@org.springframework.core.annotation.Order(2) // Runs after IdentityDataInitializer (Order 0) and UserDemoSeeder (Order 1)
@Profile({"dev", "demo", "test", "local"})
@RequiredArgsConstructor
public class PaymentDemoSeeder implements CommandLineRunner {

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final Optional<OrderRepository> orderRepository;
    private final Optional<UserRepository> userRepository;
    private final Optional<UserDemoSeeder> userDemoSeeder;

    @Override
    public void run(String... args) {
        seedDemoData();
    }

    /**
     * Seeds sample users, orders and transactions if they do not already exist.
     */
    public void seedDemoData() {
        // Ensure demo users exist first
        if (userDemoSeeder.isPresent()) {
            try {
                userDemoSeeder.get().seedDemoUsers();
            } catch (Exception ex) {
                log.warn("Could not seed demo users: {}", ex.getMessage());
            }
        }

        Long senderId = 1L;
        if (userRepository.isPresent()) {
            senderId = userRepository.get().findIdByIdentifier("sender@fptpost.vn").orElse(1L);
        }

        log.info("--> Initializing VNPay testing sample data (linked to Sender ID: {})...", senderId);

        seedOrder("ORD-TEST-001", "Tài liệu hợp đồng kinh tế", BigDecimal.valueOf(150000), OrderStatus.PENDING, senderId);
        seedOrder("ORD-TEST-002", "Gói quà lưu niệm công nghệ", BigDecimal.valueOf(250000), OrderStatus.PENDING, senderId);
        seedOrder("ORD-TEST-003", "Thiết bị điện tử chuyển phát nhanh", BigDecimal.valueOf(500000), OrderStatus.FINDING_SHIPPER, senderId);
        seedOrder("ORD-TEST-004", "Bưu phẩm giá trị cao FPTPost", BigDecimal.valueOf(1000000), OrderStatus.PENDING, senderId);

        // 1. ORD-TEST-001: PENDING transaction (Ready to test IPN / Return approval)
        if (!paymentTransactionRepository.existsByTxnRef("ORD-TEST-001")) {
            paymentTransactionRepository.save(PaymentTransaction.builder()
                    .txnRef("ORD-TEST-001")
                    .orderCode("ORD-TEST-001")
                    .amount(BigDecimal.valueOf(150000))
                    .orderInfo("Thanh toan don hang test ORD-TEST-001")
                    .bankCode("NCB")
                    .status(PaymentStatus.PENDING)
                    .ipAddress("127.0.0.1")
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build());
            log.info("Created sample PENDING transaction: ORD-TEST-001 (150,000 VND)");
        }

        // Note: ORD-TEST-002 has NO payment transaction yet, perfect for testing POST /create-vnpay-url from scratch!

        // 2. ORD-TEST-003: SUCCESS transaction (For testing Idempotency - duplicate IPN response code 02)
        if (!paymentTransactionRepository.existsByTxnRef("ORD-TEST-003")) {
            paymentTransactionRepository.save(PaymentTransaction.builder()
                    .txnRef("ORD-TEST-003")
                    .orderCode("ORD-TEST-003")
                    .amount(BigDecimal.valueOf(500000))
                    .orderInfo("Thanh toan don hang test ORD-TEST-003")
                    .bankCode("NCB")
                    .bankTranNo("VNP14567890")
                    .cardType("ATM")
                    .transactionNo("14567890")
                    .responseCode("00")
                    .transactionStatus("00")
                    .status(PaymentStatus.SUCCESS)
                    .payDate("20261013180000")
                    .ipAddress("127.0.0.1")
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build());
            log.info("Created sample SUCCESS transaction: ORD-TEST-003 (500,000 VND - Idempotency test)");
        }

        // 3. ORD-TEST-004: CANCELLED transaction (For testing customer cancellation response code 24)
        if (!paymentTransactionRepository.existsByTxnRef("ORD-TEST-004")) {
            paymentTransactionRepository.save(PaymentTransaction.builder()
                    .txnRef("ORD-TEST-004")
                    .orderCode("ORD-TEST-004")
                    .amount(BigDecimal.valueOf(1000000))
                    .orderInfo("Thanh toan don hang test ORD-TEST-004")
                    .bankCode("NCB")
                    .responseCode("24")
                    .transactionStatus("02")
                    .status(PaymentStatus.CANCELLED)
                    .ipAddress("127.0.0.1")
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build());
            log.info("Created sample CANCELLED transaction: ORD-TEST-004 (1,000,000 VND)");
        }

        log.info("--> VNPay sample data initialized successfully!");
    }

    private void seedOrder(String orderCode, String productName, BigDecimal amount, OrderStatus status, Long senderId) {
        if (orderRepository.isEmpty()) {
            return;
        }

        OrderRepository repo = orderRepository.get();
        if (!repo.existsByOrderCode(orderCode)) {
            Order order = Order.builder()
                    .orderCode(orderCode)
                    .senderId(senderId)
                    .pickupAddress("Khu Công Nghệ Cao Hòa Lạc, Thạch Thất, Hà Nội")
                    .deliveryAddress("Tòa nhà FPT, Cầu Giấy, Hà Nội")
                    .receiverName("Nguyễn Văn Test")
                    .receiverPhone("0987654321")
                    .productName(productName)
                    .quantity(1)
                    .weightKg(BigDecimal.valueOf(1.2))
                    .declaredValue(amount)
                    .deliveryNote("Hàng mẫu kiểm thử thanh toán VNPay FPTPost")
                    .status(status)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();
            repo.save(order);
            log.info("Created sample Shipping Order: {} ({}) with status {} for Sender ID {}", orderCode, productName, status, senderId);
        }
    }
}
