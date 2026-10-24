package com.fptpost.modules.payment.service;

import com.fptpost.common.exception.AppException;
import com.fptpost.common.response.PageResponse;
import com.fptpost.modules.payment.config.VNPayConfig;
import com.fptpost.modules.payment.dto.*;
import com.fptpost.modules.payment.exception.PaymentErrorCode;
import com.fptpost.modules.payment.model.*;
import com.fptpost.modules.payment.repository.LedgerEntryRepository;
import com.fptpost.modules.payment.repository.LedgerTransactionRepository;
import com.fptpost.modules.payment.repository.PaymentTransactionRepository;
import com.fptpost.modules.payment.repository.WalletRepository;
import com.fptpost.modules.payment.util.VNPayUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

    private static final long MIN_TOPUP = 20_000L;
    private static final long MAX_TOPUP = 10_000_000L;
    private static final long MAX_BALANCE = 20_000_000L;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final WalletRepository walletRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final LedgerTransactionRepository ledgerTransactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final VNPayConfig vnPayConfig;

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

    /**
     * Lấy danh sách phân trang lịch sử biến động số dư ví (UC-38, API-089).
     */
    @Transactional(readOnly = true)
    public PageResponse<WalletTransactionResponse> getTransactionHistory(Long userId, WalletTransactionFilterRequest request) {
        if (request == null) {
            request = new WalletTransactionFilterRequest();
        }

        int page = Math.max(0, request.getPage());
        int size = request.getSize() > 0 && request.getSize() <= 100 ? request.getSize() : 10;
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));

        String accountCode = "USER_WALLET_" + userId;
        Page<LedgerTransaction> pageResult = ledgerTransactionRepository.findTransactions(
                userId,
                accountCode,
                request.getType(),
                request.getFromDate(),
                request.getToDate(),
                pageable
        );

        List<WalletTransactionResponse> dtoList = pageResult.getContent().stream()
                .map(txn -> mapToTransactionResponse(txn, userId))
                .toList();

        return new PageResponse<>(
                dtoList,
                pageResult.getNumber(),
                pageResult.getSize(),
                pageResult.getTotalElements(),
                pageResult.getTotalPages(),
                pageResult.isFirst(),
                pageResult.isLast()
        );
    }

    /**
     * Bảng sao kê biến động tài khoản ví theo tháng/năm (UC-38, API-090).
     */
    @Transactional(readOnly = true)
    public WalletStatementResponse getMonthlyStatement(Long userId, int month, int year) {
        if (month < 1 || month > 12 || year < 2020 || year > 2100) {
            log.warn("Invalid statement period requested: month={}, year={}", month, year);
            throw new AppException(PaymentErrorCode.WALLET_STATEMENT_PERIOD_INVALID);
        }

        getOrCreateWallet(userId);
        String period = String.format("%02d/%d", month, year);
        String accountCode = "USER_WALLET_" + userId;

        ZonedDateTime startOfMonth = ZonedDateTime.of(year, month, 1, 0, 0, 0, 0, VIETNAM_ZONE);
        ZonedDateTime endOfMonth = startOfMonth.plusMonths(1).minusNanos(1);
        Instant startInstant = startOfMonth.toInstant();
        Instant endInstant = endOfMonth.toInstant();

        Long rawStarting = ledgerEntryRepository.calculateBalanceBefore(accountCode, startInstant);
        long startingBalance = rawStarting != null ? Math.max(0L, rawStarting) : 0L;

        List<LedgerTransaction> txns = ledgerTransactionRepository.findTransactionsInRange(
                userId, accountCode, startInstant, endInstant);

        long totalInflow = 0L;
        long totalOutflow = 0L;
        List<WalletTransactionResponse> transactionResponses = new ArrayList<>();

        for (LedgerTransaction txn : txns) {
            WalletTransactionResponse resp = mapToTransactionResponse(txn, userId);
            if ("IN".equalsIgnoreCase(resp.getBalanceDirection())) {
                totalInflow += resp.getAmount();
            } else {
                totalOutflow += resp.getAmount();
            }
            transactionResponses.add(resp);
        }

        long endingBalance = startingBalance + totalInflow - totalOutflow;
        long netChange = totalInflow - totalOutflow;

        return WalletStatementResponse.builder()
                .userId(userId)
                .period(period)
                .startingBalance(startingBalance)
                .endingBalance(endingBalance)
                .totalInflow(totalInflow)
                .totalOutflow(totalOutflow)
                .netChange(netChange)
                .transactionCount(transactionResponses.size())
                .transactions(transactionResponses)
                .build();
    }

    private WalletTransactionResponse mapToTransactionResponse(LedgerTransaction txn, Long userId) {
        String userAccount = "USER_WALLET_" + userId;
        Optional<LedgerEntry> userEntryOpt = txn.getEntries() != null
                ? txn.getEntries().stream().filter(e -> userAccount.equals(e.getAccountCode())).findFirst()
                : Optional.empty();

        String direction;
        long amount;

        if (userEntryOpt.isPresent()) {
            LedgerEntry entry = userEntryOpt.get();
            if (entry.getCredit() != null && entry.getCredit() > 0) {
                direction = "IN";
                amount = entry.getCredit();
            } else if (entry.getDebit() != null && entry.getDebit() > 0) {
                direction = "OUT";
                amount = entry.getDebit();
            } else {
                direction = "IN";
                amount = txn.getAmount() != null ? txn.getAmount() : 0L;
            }
        } else {
            if (txn.getType() == LedgerTransactionType.WALLET_TOPUP || txn.getType() == LedgerTransactionType.ESCROW_RELEASE) {
                direction = "IN";
            } else {
                direction = "OUT";
            }
            amount = txn.getAmount() != null ? txn.getAmount() : 0L;
        }

        return WalletTransactionResponse.builder()
                .transactionCode(txn.getTransactionCode())
                .type(txn.getType())
                .amount(amount)
                .status(txn.getStatus())
                .balanceDirection(direction)
                .description(txn.getDescription())
                .orderId(txn.getOrderId())
                .refType(txn.getRefType())
                .refId(txn.getRefId())
                .createdAt(txn.getCreatedAt())
                .build();
    }
}

