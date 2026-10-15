package com.fptpost.modules.payment.service;

import com.fptpost.modules.payment.model.*;
import com.fptpost.modules.payment.repository.LedgerTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerService {

    private final LedgerTransactionRepository ledgerTransactionRepository;

    @Transactional
    public LedgerTransaction recordWalletTopup(Long userId, Long amount, String txnRef) {
        String idemKey = "TOPUP_" + txnRef;
        if (ledgerTransactionRepository.existsByIdempotencyKey(idemKey)) {
            log.info("Ledger entry already exists for top-up txnRef: {}", txnRef);
            return ledgerTransactionRepository.findByIdempotencyKey(idemKey).orElse(null);
        }

        String txnCode = "LTX-TOPUP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        LedgerTransaction txn = LedgerTransaction.builder()
                .transactionCode(txnCode)
                .type(LedgerTransactionType.WALLET_TOPUP)
                .status(LedgerTransactionStatus.COMPLETED)
                .idempotencyKey(idemKey)
                .refType("PAYMENT_TRANSACTION")
                .refId(txnRef)
                .amount(amount)
                .createdBy(userId)
                .description("Nạp tiền vào ví người dùng ID " + userId + " qua VNPay")
                .build();

        // Ghi Nợ: VNPAY_CLEARING (Tài sản thanh toán trung gian)
        txn.addEntry(LedgerEntry.builder()
                .accountCode("VNPAY_CLEARING")
                .accountType("ASSET")
                .debit(amount)
                .credit(0L)
                .build());

        // Ghi Có: USER_WALLET_{userId} (Nghĩa vụ phải trả với khách hàng)
        txn.addEntry(LedgerEntry.builder()
                .accountCode("USER_WALLET_" + userId)
                .accountType("LIABILITY")
                .debit(0L)
                .credit(amount)
                .build());

        LedgerTransaction saved = ledgerTransactionRepository.save(txn);
        log.info("Saved ledger transaction for wallet topup: txnCode={}, amount={}", txnCode, amount);
        return saved;
    }

    @Transactional
    public LedgerTransaction recordOrderEscrowFromWallet(Long orderId, Long senderId, Long amount, String escrowRef) {
        String idemKey = "ESCROW_WALLET_" + orderId + "_" + escrowRef;
        if (ledgerTransactionRepository.existsByIdempotencyKey(idemKey)) {
            return ledgerTransactionRepository.findByIdempotencyKey(idemKey).orElse(null);
        }

        String txnCode = "LTX-ESCROW-W-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        LedgerTransaction txn = LedgerTransaction.builder()
                .transactionCode(txnCode)
                .type(LedgerTransactionType.ORDER_ESCROW_HOLD)
                .status(LedgerTransactionStatus.COMPLETED)
                .idempotencyKey(idemKey)
                .orderId(orderId)
                .refType("ESCROW")
                .refId(escrowRef)
                .amount(amount)
                .createdBy(senderId)
                .description("Ký quỹ đơn hàng #" + orderId + " từ ví người gửi ID " + senderId)
                .build();

        // Ghi Nợ: USER_WALLET_{senderId} (Giảm nghĩa vụ ví người gửi)
        txn.addEntry(LedgerEntry.builder()
                .accountCode("USER_WALLET_" + senderId)
                .accountType("LIABILITY")
                .debit(amount)
                .credit(0L)
                .build());

        // Ghi Có: ESCROW_ORDER_{orderId} (Tăng khoản ký quỹ đơn hàng giam giữ)
        txn.addEntry(LedgerEntry.builder()
                .accountCode("ESCROW_ORDER_" + orderId)
                .accountType("LIABILITY")
                .debit(0L)
                .credit(amount)
                .build());

        LedgerTransaction saved = ledgerTransactionRepository.save(txn);
        log.info("Saved ledger transaction for order escrow from wallet: txnCode={}, orderId={}", txnCode, orderId);
        return saved;
    }

    @Transactional
    public LedgerTransaction recordOrderEscrowFromVNPay(Long orderId, Long senderId, Long amount, String txnRef) {
        String idemKey = "ESCROW_VNPAY_" + txnRef;
        if (ledgerTransactionRepository.existsByIdempotencyKey(idemKey)) {
            return ledgerTransactionRepository.findByIdempotencyKey(idemKey).orElse(null);
        }

        String txnCode = "LTX-ESCROW-VNP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        LedgerTransaction txn = LedgerTransaction.builder()
                .transactionCode(txnCode)
                .type(LedgerTransactionType.ORDER_ESCROW_HOLD)
                .status(LedgerTransactionStatus.COMPLETED)
                .idempotencyKey(idemKey)
                .orderId(orderId)
                .refType("PAYMENT_TRANSACTION")
                .refId(txnRef)
                .amount(amount)
                .createdBy(senderId)
                .description("Ký quỹ đơn hàng #" + orderId + " trực tiếp qua VNPay")
                .build();

        // Ghi Nợ: VNPAY_CLEARING (Tài sản thanh toán trung gian VNPay)
        txn.addEntry(LedgerEntry.builder()
                .accountCode("VNPAY_CLEARING")
                .accountType("ASSET")
                .debit(amount)
                .credit(0L)
                .build());

        // Ghi Có: ESCROW_ORDER_{orderId} (Tăng khoản ký quỹ đơn hàng giam giữ)
        txn.addEntry(LedgerEntry.builder()
                .accountCode("ESCROW_ORDER_" + orderId)
                .accountType("LIABILITY")
                .debit(0L)
                .credit(amount)
                .build());

        LedgerTransaction saved = ledgerTransactionRepository.save(txn);
        log.info("Saved ledger transaction for order escrow from VNPay: txnCode={}, orderId={}", txnCode, orderId);
        return saved;
    }
}
