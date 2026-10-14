package com.fptpost.modules.payment.service.impl;

import com.fptpost.common.exception.AppException;
import com.fptpost.modules.order.exception.OrderErrorCode;
import com.fptpost.modules.order.model.Order;
import com.fptpost.modules.order.model.OrderStatus;
import com.fptpost.modules.order.repository.OrderRepository;
import com.fptpost.modules.payment.config.VNPayConfig;
import com.fptpost.modules.payment.dto.OrderPaymentRequest;
import com.fptpost.modules.payment.dto.OrderPaymentResponse;
import com.fptpost.modules.payment.exception.PaymentErrorCode;
import com.fptpost.modules.payment.model.*;
import com.fptpost.modules.payment.repository.EscrowRepository;
import com.fptpost.modules.payment.repository.PaymentTransactionRepository;
import com.fptpost.modules.payment.repository.WalletRepository;
import com.fptpost.modules.payment.service.LedgerService;
import com.fptpost.modules.payment.service.OrderPaymentService;
import com.fptpost.modules.payment.util.VNPayUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderPaymentServiceImpl implements OrderPaymentService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final OrderRepository orderRepository;
    private final WalletRepository walletRepository;
    private final EscrowRepository escrowRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final LedgerService ledgerService;
    private final VNPayConfig vnPayConfig;

    @Override
    @Transactional
    public OrderPaymentResponse payOrder(Long userId, Long orderId, OrderPaymentRequest request, HttpServletRequest httpRequest) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(OrderErrorCode.ORDER_NOT_FOUND));

        // Kiểm tra quyền sở hữu đơn hàng (BR-F-071: chỉ người tạo đơn/SENDER mới được thanh toán)
        if (!order.getSenderId().equals(userId)) {
            log.warn("User {} attempted to pay order {} owned by sender {}", userId, orderId, order.getSenderId());
            throw new AppException(PaymentErrorCode.ORDER_PAYMENT_FORBIDDEN);
        }

        // Kiểm tra xem đơn đã được ký quỹ chưa
        if (escrowRepository.existsByOrderId(orderId) || order.getStatus() == OrderStatus.ESCROWED) {
            log.warn("Order {} is already escrowed", orderId);
            throw new AppException(PaymentErrorCode.ORDER_ALREADY_ESCROWED);
        }

        // Kiểm tra trạng thái đơn hàng có hợp lệ để thanh toán ký quỹ không
        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.PENDING_ESCROW) {
            log.warn("Order {} in status {} cannot be paid", orderId, order.getStatus());
            throw new AppException(PaymentErrorCode.ORDER_PAYMENT_NOT_ALLOWED);
        }

        // Số tiền ký quỹ cần thanh toán (tính theo giá trị khai báo hoặc cước phí)
        long orderAmount = order.getDeclaredValue() != null && order.getDeclaredValue().compareTo(BigDecimal.ZERO) > 0
                ? order.getDeclaredValue().longValue()
                : 150_000L; // Giá trị tối thiểu demo nếu đơn chưa set giá trị

        // ==========================================
        // NHÁNH 1: THANH TOÁN BẰNG SỐ DƯ VÍ (WALLET)
        // ==========================================
        if (request.getPaymentMethod() == EscrowPaymentMethod.WALLET) {
            log.info("Processing wallet payment for orderId={}, senderId={}, amount={}", orderId, userId, orderAmount);

            Wallet wallet = walletRepository.findByUserIdForUpdate(userId).orElseGet(() -> {
                Wallet newWallet = Wallet.builder()
                        .userId(userId)
                        .available(0L)
                        .escrowHeld(0L)
                        .pendingWithdraw(0L)
                        .build();
                return walletRepository.save(newWallet);
            });

            long currentAvailable = wallet.getAvailable() != null ? wallet.getAvailable() : 0L;
            if (currentAvailable < orderAmount) {
                log.warn("Insufficient wallet balance for userId={}: available={}, needed={}", userId, currentAvailable, orderAmount);
                throw new AppException(PaymentErrorCode.WALLET_INSUFFICIENT_BALANCE);
            }

            // Trừ số dư khả dụng và đưa vào khoản tiền đang ký quỹ
            wallet.setAvailable(currentAvailable - orderAmount);
            wallet.setEscrowHeld((wallet.getEscrowHeld() != null ? wallet.getEscrowHeld() : 0L) + orderAmount);
            walletRepository.save(wallet);

            // Tạo bản ghi Escrow trạng thái HELD
            Escrow escrow = Escrow.builder()
                    .orderId(orderId)
                    .senderId(userId)
                    .amount(orderAmount)
                    .status(EscrowStatus.HELD)
                    .paymentMethod(EscrowPaymentMethod.WALLET)
                    .releaseAt(Instant.now().plus(Duration.ofHours(24)))
                    .frozen(false)
                    .build();
            escrowRepository.save(escrow);

            // Ghi sổ cái kép
            ledgerService.recordOrderEscrowFromWallet(orderId, userId, orderAmount, "ESCROW_ORD_" + orderId);

            // Cập nhật trạng thái đơn hàng sang ESCROWED
            order.setStatus(OrderStatus.ESCROWED);
            orderRepository.save(order);

            log.info("Successfully paid and escrowed order {} via WALLET", orderId);

            return OrderPaymentResponse.builder()
                    .orderId(orderId)
                    .orderCode(order.getOrderCode())
                    .paymentMethod(EscrowPaymentMethod.WALLET)
                    .amount(orderAmount)
                    .status("SUCCESS")
                    .message("Ký quỹ đơn hàng bằng ví thành công")
                    .build();
        }

        // ==========================================
        // NHÁNH 2: THANH TOÁN QUA CỔNG VNPAY (VNPAY)
        // ==========================================
        log.info("Generating VNPay payment URL for orderId={}, orderCode={}, amount={}", orderId, order.getOrderCode(), orderAmount);

        String txnRef = "ORD_ESCROW_" + orderId + "_" + System.currentTimeMillis();
        long vnpAmount = orderAmount * 100L;
        String clientIp = VNPayUtil.getClientIpAddress(httpRequest);

        ZonedDateTime now = ZonedDateTime.now(VIETNAM_ZONE);
        String createDate = now.format(DATE_FORMATTER);
        String expireDate = now.plusMinutes(vnPayConfig.getTimeoutMinutes()).format(DATE_FORMATTER);
        String orderInfo = "Ky quy don hang " + order.getOrderCode();

        Map<String, String> vnpParams = new HashMap<>();
        vnpParams.put("vnp_Version", vnPayConfig.getVersion());
        vnpParams.put("vnp_Command", vnPayConfig.getCommand());
        vnpParams.put("vnp_TmnCode", vnPayConfig.getVnp_TmnCode());
        vnpParams.put("vnp_Amount", String.valueOf(vnpAmount));
        vnpParams.put("vnp_CurrCode", vnPayConfig.getCurrCode());
        vnpParams.put("vnp_TxnRef", txnRef);
        vnpParams.put("vnp_OrderInfo", orderInfo);
        vnpParams.put("vnp_OrderType", vnPayConfig.getOrderType());

        String locale = request.getLanguage() != null && !request.getLanguage().trim().isEmpty()
                ? request.getLanguage().trim()
                : vnPayConfig.getLocale();
        vnpParams.put("vnp_Locale", locale);

        vnpParams.put("vnp_ReturnUrl", vnPayConfig.getVnp_ReturnUrl());
        vnpParams.put("vnp_IpAddr", clientIp);
        vnpParams.put("vnp_CreateDate", createDate);
        vnpParams.put("vnp_ExpireDate", expireDate);

        if (request.getBankCode() != null && !request.getBankCode().trim().isEmpty()) {
            vnpParams.put("vnp_BankCode", request.getBankCode().trim());
        }

        VNPayUtil.HashDataAndQuery hashDataAndQuery = VNPayUtil.buildHashDataAndQuery(vnpParams);
        String secureHash = VNPayUtil.hmacSHA512(vnPayConfig.getVnp_HashSecret(), hashDataAndQuery.hashData());
        String paymentUrl = vnPayConfig.getVnp_PayUrl() + "?" + hashDataAndQuery.queryString() + "&vnp_SecureHash=" + secureHash;

        PaymentTransaction transaction = PaymentTransaction.builder()
                .txnRef(txnRef)
                .userId(userId)
                .orderId(orderId)
                .orderCode(order.getOrderCode())
                .purpose(PaymentPurpose.ORDER_ESCROW)
                .amount(BigDecimal.valueOf(orderAmount))
                .orderInfo(orderInfo)
                .bankCode(request.getBankCode())
                .status(PaymentStatus.PENDING)
                .ipAddress(clientIp)
                .expiresAt(now.plusMinutes(vnPayConfig.getTimeoutMinutes()).toInstant())
                .build();
        paymentTransactionRepository.save(transaction);

        log.info("Successfully generated VNPay payment URL for orderId={}, txnRef={}", orderId, txnRef);

        return OrderPaymentResponse.builder()
                .orderId(orderId)
                .orderCode(order.getOrderCode())
                .paymentMethod(EscrowPaymentMethod.VNPAY)
                .amount(orderAmount)
                .status("PENDING_PAYMENT")
                .paymentUrl(paymentUrl)
                .txnRef(txnRef)
                .message("Vui lòng chuyển hướng sang cổng VNPay để thanh toán ký quỹ")
                .build();
    }
}
