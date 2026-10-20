package com.fptpost.modules.payment.service.impl;

import com.fptpost.modules.order.model.OrderStatus;
import com.fptpost.modules.order.repository.OrderRepository;
import com.fptpost.modules.payment.config.VNPayConfig;
import com.fptpost.modules.payment.dto.PaymentCreationRequest;
import com.fptpost.modules.payment.dto.PaymentCreationResponse;
import com.fptpost.modules.payment.dto.VNPayIpnResponse;
import com.fptpost.modules.payment.dto.VNPayReturnResponse;
import com.fptpost.modules.payment.model.PaymentStatus;
import com.fptpost.modules.payment.model.PaymentTransaction;
import com.fptpost.modules.payment.repository.PaymentTransactionRepository;
import com.fptpost.modules.payment.service.VNPayService;
import com.fptpost.modules.payment.util.VNPayUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fptpost.modules.payment.model.Wallet;
import com.fptpost.modules.payment.model.PaymentPurpose;
import com.fptpost.modules.payment.repository.WalletRepository;
import com.fptpost.modules.payment.service.LedgerService;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Production implementation of VNPay payment gateway service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VNPayServiceImpl implements VNPayService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final VNPayConfig vnPayConfig;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final Optional<OrderRepository> orderRepository;
    private final WalletRepository walletRepository;
    private final LedgerService ledgerService;

    @Override
    @Transactional
    public PaymentCreationResponse createPaymentUrl(PaymentCreationRequest request, HttpServletRequest httpRequest) {
        log.info("Generating VNPay payment URL for orderCode={}, amount={}", request.getOrderCode(), request.getAmount());

        // Multiply amount by 100 as strictly required by VNPay (VND cents)
        long vnpAmount = request.getAmount().multiply(BigDecimal.valueOf(100)).longValue();

        // Generate unique transaction reference (TxnRef)
        String txnRef = generateTxnRef(request.getOrderCode());

        // Extract client IP address
        String clientIp = VNPayUtil.getClientIpAddress(httpRequest);

        // Calculate timestamps in GMT+7
        ZonedDateTime now = ZonedDateTime.now(VIETNAM_ZONE);
        String createDate = now.format(DATE_FORMATTER);
        String expireDate = now.plusMinutes(vnPayConfig.getTimeoutMinutes()).format(DATE_FORMATTER);

        String orderInfo = request.getOrderInfo() != null && !request.getOrderInfo().trim().isEmpty()
                ? request.getOrderInfo().trim()
                : "Thanh toan don hang " + request.getOrderCode();

        // Build VNPay Parameter Map
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

        // Build sorted, encoded query string and hash data
        VNPayUtil.HashDataAndQuery hashDataAndQuery = VNPayUtil.buildHashDataAndQuery(vnpParams);
        String secureHash = VNPayUtil.hmacSHA512(vnPayConfig.getVnp_HashSecret(), hashDataAndQuery.hashData());
        String paymentUrl = vnPayConfig.getVnp_PayUrl() + "?" + hashDataAndQuery.queryString() + "&vnp_SecureHash=" + secureHash;

        // Persist pending transaction
        PaymentTransaction transaction = PaymentTransaction.builder()
                .txnRef(txnRef)
                .orderCode(request.getOrderCode())
                .purpose(PaymentPurpose.ORDER_ESCROW)
                .amount(request.getAmount())
                .orderInfo(orderInfo)
                .bankCode(request.getBankCode())
                .status(PaymentStatus.PENDING)
                .ipAddress(clientIp)
                .expiresAt(now.plusMinutes(vnPayConfig.getTimeoutMinutes()).toInstant())
                .build();
        paymentTransactionRepository.save(transaction);

        log.info("Successfully created VNPay payment URL for txnRef={}", txnRef);

        return PaymentCreationResponse.builder()
                .code("00")
                .message("success")
                .paymentUrl(paymentUrl)
                .txnRef(txnRef)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public VNPayReturnResponse processReturn(Map<String, String> queryParams) {
        log.info("Processing VNPay synchronous return callback for txnRef={}", queryParams.get("vnp_TxnRef"));

        String secureHash = queryParams.get("vnp_SecureHash");
        boolean isValidSignature = VNPayUtil.verifySignature(queryParams, vnPayConfig.getVnp_HashSecret(), secureHash);

        String txnRef = queryParams.get("vnp_TxnRef");
        String responseCode = queryParams.get("vnp_ResponseCode");
        String transactionStatus = queryParams.get("vnp_TransactionStatus");
        String transactionNo = queryParams.get("vnp_TransactionNo");
        String bankCode = queryParams.get("vnp_BankCode");
        String bankTranNo = queryParams.get("vnp_BankTranNo");
        String cardType = queryParams.get("vnp_CardType");
        String orderInfo = queryParams.get("vnp_OrderInfo");
        String payDate = queryParams.get("vnp_PayDate");

        Long amount = null;
        if (queryParams.get("vnp_Amount") != null) {
            try {
                amount = Long.parseLong(queryParams.get("vnp_Amount")) / 100;
            } catch (NumberFormatException ignored) {
            }
        }

        // Extract orderCode from txnRef if suffixed
        String orderCode = txnRef;
        if (txnRef != null && txnRef.contains("_")) {
            orderCode = txnRef.substring(0, txnRef.lastIndexOf('_'));
        }

        boolean isSuccess = isValidSignature && "00".equals(responseCode) && "00".equals(transactionStatus);
        String message = resolveResponseMessage(isValidSignature, responseCode);

        return VNPayReturnResponse.builder()
                .orderCode(orderCode)
                .amount(amount)
                .bankCode(bankCode)
                .bankTranNo(bankTranNo)
                .cardType(cardType)
                .orderInfo(orderInfo)
                .payDate(payDate)
                .responseCode(responseCode)
                .transactionNo(transactionNo)
                .transactionStatus(transactionStatus)
                .secureHash(secureHash)
                .validSignature(isValidSignature)
                .success(isSuccess)
                .message(message)
                .build();
    }

    @Override
    @Transactional
    public VNPayIpnResponse processIpn(Map<String, String> queryParams) {
        String txnRef = queryParams.get("vnp_TxnRef");
        log.info("Incoming VNPay IPN webhook notification for txnRef={}", txnRef);

        // Step 1: Checksum verification
        String secureHash = queryParams.get("vnp_SecureHash");
        boolean isValidSignature = VNPayUtil.verifySignature(queryParams, vnPayConfig.getVnp_HashSecret(), secureHash);
        if (!isValidSignature) {
            log.warn("VNPay IPN Error [97]: Invalid Checksum for txnRef={}", txnRef);
            return VNPayIpnResponse.invalidChecksum();
        }

        // Step 2: Order existence check
        Optional<PaymentTransaction> optionalTransaction = paymentTransactionRepository.findByTxnRef(txnRef);
        if (optionalTransaction.isEmpty()) {
            // Fallback search by orderCode
            optionalTransaction = paymentTransactionRepository.findFirstByOrderCodeOrderByCreatedAtDesc(txnRef);
        }

        if (optionalTransaction.isEmpty()) {
            log.warn("VNPay IPN Error [01]: Order not found for txnRef={}", txnRef);
            return VNPayIpnResponse.orderNotFound();
        }

        PaymentTransaction transaction = optionalTransaction.get();

        // Step 3: Amount verification
        long expectedAmountInCents = transaction.getAmount().multiply(BigDecimal.valueOf(100)).longValue();
        long receivedAmountInCents;
        try {
            receivedAmountInCents = Long.parseLong(queryParams.get("vnp_Amount"));
        } catch (NumberFormatException e) {
            log.warn("VNPay IPN Error [04]: Invalid Amount format for txnRef={}", txnRef);
            return VNPayIpnResponse.invalidAmount();
        }

        if (expectedAmountInCents != receivedAmountInCents) {
            log.warn("VNPay IPN Error [04]: Amount mismatch for txnRef={}. Expected: {}, Received: {}",
                    txnRef, expectedAmountInCents, receivedAmountInCents);
            return VNPayIpnResponse.invalidAmount();
        }

        // Step 4: Idempotency verification
        if (transaction.getStatus() != PaymentStatus.PENDING) {
            log.info("VNPay IPN Notice [02]: Order already confirmed for txnRef={}, currentStatus={}",
                    txnRef, transaction.getStatus());
            return VNPayIpnResponse.orderAlreadyConfirmed();
        }

        // Step 5: Update database & order state
        String responseCode = queryParams.get("vnp_ResponseCode");
        String transactionStatus = queryParams.get("vnp_TransactionStatus");
        String transactionNo = queryParams.get("vnp_TransactionNo");
        String bankCode = queryParams.get("vnp_BankCode");
        String bankTranNo = queryParams.get("vnp_BankTranNo");
        String cardType = queryParams.get("vnp_CardType");
        String payDate = queryParams.get("vnp_PayDate");

        transaction.setResponseCode(responseCode);
        transaction.setTransactionStatus(transactionStatus);
        transaction.setTransactionNo(transactionNo);
        transaction.setBankCode(bankCode);
        transaction.setBankTranNo(bankTranNo);
        transaction.setCardType(cardType);
        transaction.setPayDate(payDate);

        boolean isSuccess = "00".equals(responseCode) && "00".equals(transactionStatus);
        if (isSuccess) {
            transaction.setStatus(PaymentStatus.SUCCESS);
            log.info("VNPay Payment SUCCESS for txnRef={}, purpose={}", txnRef, transaction.getPurpose());

            // 1. UC-34: Nạp tiền vào ví qua VNPay
            if (transaction.getPurpose() == PaymentPurpose.WALLET_TOPUP) {
                if (transaction.getUserId() != null) {
                    Wallet wallet = walletRepository.findByUserIdForUpdate(transaction.getUserId()).orElseGet(() ->
                            walletRepository.save(Wallet.builder().userId(transaction.getUserId()).available(0L).escrowHeld(0L).pendingWithdraw(0L).build())
                    );
                    long current = wallet.getAvailable() != null ? wallet.getAvailable() : 0L;
                    long addAmount = transaction.getAmount().longValue();
                    wallet.setAvailable(current + addAmount);
                    walletRepository.save(wallet);

                    ledgerService.recordWalletTopup(transaction.getUserId(), addAmount, txnRef);
                    log.info("Successfully credited {} VND to wallet of user {} via IPN", addAmount, transaction.getUserId());
                }
            } else {
                // Fallback cho đơn hàng cũ
                updateShippingOrderIfPresent(transaction.getOrderCode(), OrderStatus.FINDING_SHIPPER);
            }
        } else {
            transaction.setStatus("24".equals(responseCode) ? PaymentStatus.CANCELLED : PaymentStatus.FAILED);
            log.warn("VNPay Payment FAILED/CANCELLED for txnRef={}, responseCode={}, transactionStatus={}",
                    txnRef, responseCode, transactionStatus);
        }

        paymentTransactionRepository.save(transaction);

        // Step 6: Return exact VNPay acknowledgment
        return VNPayIpnResponse.success();
    }

    private String generateTxnRef(String orderCode) {
        if (!paymentTransactionRepository.existsByTxnRef(orderCode)) {
            return orderCode;
        }
        // If orderCode already exists (e.g., retried payment), append timestamp
        return orderCode + "_" + System.currentTimeMillis();
    }

    private void updateShippingOrderIfPresent(String orderCode, OrderStatus targetStatus) {
        if (orderRepository.isPresent()) {
            try {
                orderRepository.get().findByOrderCode(orderCode).ifPresent(order -> {
                    order.setStatus(targetStatus);
                    orderRepository.get().save(order);
                    log.info("Updated shipping order {} to status {}", orderCode, targetStatus);
                });
            } catch (Exception e) {
                log.error("Failed to update shipping order status for orderCode {}: {}", orderCode, e.getMessage());
            }
        }
    }

    private String resolveResponseMessage(boolean isValidSignature, String responseCode) {
        if (!isValidSignature) {
            return "Chữ ký không hợp lệ (Invalid Checksum)";
        }
        if (responseCode == null) {
            return "Không xác định được mã phản hồi";
        }
        return switch (responseCode) {
            case "00" -> "Giao dịch thành công";
            case "07" -> "Trừ tiền thành công. Giao dịch bị nghi ngờ (liên quan tới gian lận, bất thường).";
            case "09" -> "Thẻ/Tài khoản chưa đăng ký dịch vụ Internet Banking tại ngân hàng.";
            case "10" -> "Khách hàng xác thực thông tin thẻ/tài khoản không đúng quá 3 lần.";
            case "11" -> "Đã hết hạn chờ thanh toán. Xin quý khách vui lòng thực hiện lại giao dịch.";
            case "12" -> "Thẻ/Tài khoản bị khóa.";
            case "13" -> "Quý khách nhập sai mật khẩu xác thực giao dịch (OTP).";
            case "24" -> "Khách hàng hủy giao dịch.";
            case "51" -> "Tài khoản của quý khách không đủ số dư để thực hiện giao dịch.";
            case "65" -> "Tài khoản đã vượt quá hạn mức giao dịch trong ngày.";
            case "75" -> "Ngân hàng thanh toán đang bảo trì.";
            case "79" -> "Nhập sai mật khẩu thanh toán quá số lần quy định.";
            default -> "Giao dịch không thành công (Mã lỗi: " + responseCode + ")";
        };
    }
}
