package com.fptpost.modules.dispute.service;

import com.fptpost.modules.dispute.model.Complaint;
import com.fptpost.modules.payment.model.*;
import com.fptpost.modules.payment.repository.EscrowRepository;
import com.fptpost.modules.payment.repository.LedgerTransactionRepository;
import com.fptpost.modules.payment.repository.WalletRepository;
import com.fptpost.modules.payment.service.LedgerService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Thực hiện bút toán của phán quyết cuối cùng (BR-R-077).
 * Ký quỹ còn giữ được chia cho hai bên; ký quỹ đã giải ngân thì thu hồi từ số dư khả dụng của người giao,
 * phần không thu hồi được ghi nhận trên hồ sơ để Admin xử lý.
 */
@Component
@RequiredArgsConstructor
class ComplaintSettlement {

    private final EscrowRepository escrows;
    private final WalletRepository wallets;
    private final LedgerTransactionRepository ledger;
    private final LedgerService ledgerService;
    private final EntityManager entityManager;

    record Result(long refunded, long courierPaid, long clawedBack, long unrecovered) {
    }

    Optional<Escrow> escrow(Long orderId) {
        return escrows.findByOrderId(orderId);
    }

    /** Ký quỹ phục vụ tính phán quyết: số tiền đang giữ hoặc đã giải ngân của đơn. */
    long escrowAmount(Escrow escrow) {
        return escrow == null || escrow.getAmount() == null ? 0 : escrow.getAmount();
    }

    void freeze(Long orderId) {
        escrows.findByOrderId(orderId).map(this::lock)
                .filter(escrow -> escrow.getStatus() == EscrowStatus.HELD || escrow.getStatus() == EscrowStatus.RELEASED)
                .ifPresent(escrow -> escrow.setFrozen(true));
    }

    Result settle(Complaint complaint, Long senderId, Long courierId, Instant now) {
        Escrow escrow = escrows.findByOrderId(complaint.getOrderId()).map(this::lock).orElse(null);
        long refund = value(complaint.getRefundAmount());
        if (escrow == null) return new Result(0, 0, 0, refund);
        try {
            if (escrow.getStatus() == EscrowStatus.HELD) return splitHeld(complaint, escrow, senderId, courierId, refund, now);
            if (escrow.getStatus() == EscrowStatus.RELEASED && refund > 0 && courierId != null) {
                return clawBack(complaint, escrow, senderId, courierId, refund);
            }
            return new Result(0, 0, 0, 0);
        } finally {
            escrow.setFrozen(false);
        }
    }

    private Result splitHeld(Complaint complaint, Escrow escrow, Long senderId, Long courierId, long refund, Instant now) {
        long amount = escrowAmount(escrow);
        long courierShare = courierId == null ? 0 : amount - refund;
        long senderShare = amount - courierShare;
        if (escrow.getPaymentMethod() == EscrowPaymentMethod.WALLET) {
            Wallet sender = wallet(senderId);
            sender.setEscrowHeld(Math.max(0L, value(sender.getEscrowHeld()) - amount));
            sender.setAvailable(value(sender.getAvailable()) + senderShare);
        } else if (senderShare > 0) {
            Wallet sender = wallet(senderId);
            sender.setAvailable(value(sender.getAvailable()) + senderShare);
        }
        if (senderShare > 0) {
            post(LedgerTransactionType.ESCROW_REFUND, "COMPLAINT_REFUND_" + complaint.getId(), complaint, senderShare, senderId,
                    "Hoàn ký quỹ đơn #" + complaint.getOrderId() + " theo phán quyết khiếu nại " + complaint.getCode(),
                    "ESCROW_ORDER_" + complaint.getOrderId(), "USER_WALLET_" + senderId);
        }
        if (courierShare > 0) {
            Wallet courier = wallet(courierId);
            courier.setAvailable(value(courier.getAvailable()) + courierShare);
            ledgerService.recordEscrowRelease(complaint.getOrderId(), senderId, courierId, courierShare,
                    "ESCROW_ORD_" + complaint.getOrderId());
            escrow.setReleaseAt(now);
        }
        escrow.setStatus(courierShare == 0 ? EscrowStatus.REFUNDED
                : senderShare == 0 ? EscrowStatus.RELEASED : EscrowStatus.PARTIALLY_REFUNDED);
        return new Result(senderShare, courierShare, 0, 0);
    }

    private Result clawBack(Complaint complaint, Escrow escrow, Long senderId, Long courierId, long refund) {
        Wallet courier = wallet(courierId);
        long taken = Math.min(refund, Math.max(0L, value(courier.getAvailable())));
        if (taken > 0) {
            courier.setAvailable(value(courier.getAvailable()) - taken);
            Wallet sender = wallet(senderId);
            sender.setAvailable(value(sender.getAvailable()) + taken);
            post(LedgerTransactionType.COMPLAINT_CLAWBACK, "COMPLAINT_CLAWBACK_" + complaint.getId(), complaint, taken, courierId,
                    "Thu hồi từ người giao theo phán quyết khiếu nại " + complaint.getCode() + " của đơn #" + escrow.getOrderId(),
                    "USER_WALLET_" + courierId, "USER_WALLET_" + senderId);
        }
        return new Result(0, 0, taken, refund - taken);
    }

    private void post(LedgerTransactionType type, String key, Complaint complaint, long amount, Long createdBy,
                      String description, String debitAccount, String creditAccount) {
        if (ledger.existsByIdempotencyKey(key)) return;
        LedgerTransaction transaction = LedgerTransaction.builder()
                .transactionCode("LTX-CMP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .type(type)
                .status(LedgerTransactionStatus.COMPLETED)
                .idempotencyKey(key)
                .orderId(complaint.getOrderId())
                .refType("COMPLAINT")
                .refId(complaint.getPublicId().toString())
                .amount(amount)
                .createdBy(createdBy)
                .description(description)
                .build();
        transaction.addEntry(LedgerEntry.builder().accountCode(debitAccount).accountType("LIABILITY").debit(amount).credit(0L).build());
        transaction.addEntry(LedgerEntry.builder().accountCode(creditAccount).accountType("LIABILITY").debit(0L).credit(amount).build());
        ledger.save(transaction);
    }

    private Escrow lock(Escrow escrow) {
        return entityManager.find(Escrow.class, escrow.getId(), LockModeType.PESSIMISTIC_WRITE);
    }

    private Wallet wallet(Long userId) {
        return wallets.findByUserIdForUpdate(userId).orElseGet(() -> wallets.save(Wallet.builder()
                .userId(userId).available(0L).escrowHeld(0L).pendingWithdraw(0L).build()));
    }

    private static long value(Long amount) {
        return amount == null ? 0 : amount;
    }
}
