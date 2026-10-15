package com.fptpost.modules.payment.service.impl;

import com.fptpost.modules.payment.model.*;
import com.fptpost.modules.payment.repository.LedgerTransactionRepository;
import com.fptpost.modules.payment.service.LedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerServiceImpl implements LedgerService {

    private final LedgerTransactionRepository ledgerTransactionRepository;

    @Override
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
}
