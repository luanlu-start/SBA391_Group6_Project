package com.fptpost.modules.payment.service.impl;

import com.fptpost.common.exception.AppException;
import com.fptpost.modules.payment.config.VNPayConfig;
import com.fptpost.modules.payment.dto.PaymentCreationResponse;
import com.fptpost.modules.payment.dto.WalletResponse;
import com.fptpost.modules.payment.dto.WalletTopupRequest;
import com.fptpost.modules.payment.exception.PaymentErrorCode;
import com.fptpost.modules.payment.model.PaymentPurpose;
import com.fptpost.modules.payment.model.PaymentStatus;
import com.fptpost.modules.payment.model.PaymentTransaction;
import com.fptpost.modules.payment.model.Wallet;
import com.fptpost.modules.payment.repository.PaymentTransactionRepository;
import com.fptpost.modules.payment.repository.WalletRepository;
import com.fptpost.modules.payment.service.WalletService;
import com.fptpost.modules.payment.util.VNPayUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private static final long MIN_TOPUP = 20_000L;
    private static final long MAX_TOPUP = 10_000_000L;
    private static final long MAX_BALANCE = 20_000_000L;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final WalletRepository walletRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final VNPayConfig vnPayConfig;

    @Override
    @Transactional
    public Wallet getOrCreateWallet(Long userId) {
        return walletRepository.findByUserId(userId).orElseGet(() -> {
            log.info("Lazy-initializing new wallet for userId={}", userId);
            Wallet newWallet = Wallet.builder()
                    .userId(userId)
                    .available(0L)
                    .escrowHeld(0L)
                    .pendingWithdraw(0L)
                    .build();
            return walletRepository.save(newWallet);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public WalletResponse getWallet(Long userId) {
        Wallet wallet = walletRepository.findByUserId(userId).orElseGet(() ->
                Wallet.builder()
                        .userId(userId)
                        .available(0L)
                        .escrowHeld(0L)
                        .pendingWithdraw(0L)
                        .updatedAt(Instant.now())
                        .build()
        );

        long available = wallet.getAvailable() != null ? wallet.getAvailable() : 0L;
        long escrowHeld = wallet.getEscrowHeld() != null ? wallet.getEscrowHeld() : 0L;
        long pendingWithdraw = wallet.getPendingWithdraw() != null ? wallet.getPendingWithdraw() : 0L;
        long totalBalance = available + escrowHeld;

        return WalletResponse.builder()
                .id(wallet.getId())
                .userId(userId)
                .available(available)
                .escrowHeld(escrowHeld)
                .pendingWithdraw(pendingWithdraw)
                .totalBalance(totalBalance)
                .updatedAt(wallet.getUpdatedAt() != null ? wallet.getUpdatedAt() : Instant.now())
                .build();
    }

    @Override
    @Transactional
    public PaymentCreationResponse createTopupUrl(Long userId, WalletTopupRequest request, HttpServletRequest httpRequest) {
        long topupAmount = request.getAmount().longValue();

        if (topupAmount < MIN_TOPUP || topupAmount > MAX_TOPUP) {
            log.warn("Invalid topup amount: {} (allowed: {} - {})", topupAmount, MIN_TOPUP, MAX_TOPUP);
            throw new AppException(PaymentErrorCode.WALLET_TOPUP_AMOUNT_INVALID);
        }

        Wallet wallet = getOrCreateWallet(userId);
        long currentAvailable = wallet.getAvailable() != null ? wallet.getAvailable() : 0L;
        if (currentAvailable + topupAmount > MAX_BALANCE) {
            log.warn("Topup rejected: current balance {} + topup {} > max allowed {}", currentAvailable, topupAmount, MAX_BALANCE);
            throw new AppException(PaymentErrorCode.WALLET_MAX_BALANCE_EXCEEDED);
        }

        // Generate unique TxnRef for top-up
        String txnRef = "TOPUP_" + userId + "_" + System.currentTimeMillis();

        // Calculate amount in VNPay cents
        long vnpAmount = request.getAmount().multiply(BigDecimal.valueOf(100)).longValue();

        String clientIp = VNPayUtil.getClientIpAddress(httpRequest);
        ZonedDateTime now = ZonedDateTime.now(VIETNAM_ZONE);
        String createDate = now.format(DATE_FORMATTER);
        String expireDate = now.plusMinutes(vnPayConfig.getTimeoutMinutes()).format(DATE_FORMATTER);
        String orderInfo = "Nap tien vao vi FPTPost tai khoan " + userId;

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
                .purpose(PaymentPurpose.WALLET_TOPUP)
                .amount(request.getAmount())
                .orderInfo(orderInfo)
                .bankCode(request.getBankCode())
                .status(PaymentStatus.PENDING)
                .ipAddress(clientIp)
                .expiresAt(now.plusMinutes(vnPayConfig.getTimeoutMinutes()).toInstant())
                .build();
        paymentTransactionRepository.save(transaction);

        log.info("Successfully created VNPay topup URL for userId={}, txnRef={}", userId, txnRef);

        return PaymentCreationResponse.builder()
                .code("00")
                .message("success")
                .paymentUrl(paymentUrl)
                .txnRef(txnRef)
                .build();
    }
}
