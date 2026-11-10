package com.fptpost.modules.payment.service;

import com.fptpost.common.exception.AppException;
import com.fptpost.modules.payment.dto.LedgerIntegrityResponse;
import com.fptpost.modules.payment.dto.ReconciliationAdjustRequest;
import com.fptpost.modules.payment.dto.ReconciliationItemResponse;
import com.fptpost.modules.payment.dto.ReconciliationReportResponse;
import com.fptpost.modules.payment.exception.PaymentErrorCode;
import com.fptpost.modules.payment.model.LedgerEntry;
import com.fptpost.modules.payment.model.LedgerTransaction;
import com.fptpost.modules.payment.model.LedgerTransactionType;
import com.fptpost.modules.payment.model.PaymentPurpose;
import com.fptpost.modules.payment.model.PaymentStatus;
import com.fptpost.modules.payment.model.PaymentTransaction;
import com.fptpost.modules.payment.model.ReconciliationDiscrepancyType;
import com.fptpost.modules.payment.model.ReconciliationItem;
import com.fptpost.modules.payment.model.ReconciliationItemStatus;
import com.fptpost.modules.payment.model.ReconciliationReport;
import com.fptpost.modules.payment.model.ReconciliationReportStatus;
import com.fptpost.modules.payment.repository.LedgerTransactionRepository;
import com.fptpost.modules.payment.repository.PaymentTransactionRepository;
import com.fptpost.modules.payment.repository.ReconciliationItemRepository;
import com.fptpost.modules.payment.repository.ReconciliationReportRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReconciliationService Unit Tests (UC-43)")
class ReconciliationServiceTest {

    @Mock
    private ReconciliationReportRepository reportRepository;

    @Mock
    private ReconciliationItemRepository itemRepository;

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    @Mock
    private LedgerTransactionRepository ledgerTransactionRepository;

    @Mock
    private LedgerService ledgerService;

    @InjectMocks
    private ReconciliationService reconciliationService;

    @Test
    @DisplayName("API-107: Chạy đối soát phát hiện dòng khớp và dòng lệch (MISSING_LEDGER), không tự ý sửa số dư (BR-R-062)")
    void runReconciliation_DetectsMatchesAndMismatches() {
        LocalDate targetDate = LocalDate.of(2026, 11, 10);

        PaymentTransaction matchedPtx = PaymentTransaction.builder()
                .id(1L)
                .txnRef("TOPUP_MATCH_001")
                .purpose(PaymentPurpose.WALLET_TOPUP)
                .amount(BigDecimal.valueOf(100_000L))
                .status(PaymentStatus.SUCCESS)
                .createdAt(Instant.now())
                .build();

        PaymentTransaction missingLedgerPtx = PaymentTransaction.builder()
                .id(2L)
                .txnRef("TOPUP_MISSING_002")
                .purpose(PaymentPurpose.WALLET_TOPUP)
                .amount(BigDecimal.valueOf(200_000L))
                .status(PaymentStatus.SUCCESS)
                .createdAt(Instant.now())
                .build();

        LedgerTransaction matchedLtx = LedgerTransaction.builder()
                .id(10L)
                .transactionCode("LTX-TOPUP-001")
                .type(LedgerTransactionType.WALLET_TOPUP)
                .refType("PAYMENT_TRANSACTION")
                .refId("TOPUP_MATCH_001")
                .amount(100_000L)
                .build();

        when(paymentTransactionRepository.findByCreatedAtBetweenOrderByCreatedAtAsc(any(), any()))
                .thenReturn(List.of(matchedPtx, missingLedgerPtx));
        when(ledgerTransactionRepository.findByCreatedAtBetweenOrderByCreatedAtAsc(any(), any()))
                .thenReturn(List.of(matchedLtx));
        when(reportRepository.save(any(ReconciliationReport.class))).thenAnswer(inv -> {
            ReconciliationReport r = inv.getArgument(0);
            r.setId(500L);
            return r;
        });

        ReconciliationReportResponse report = reconciliationService.runReconciliation(targetDate, 99L);

        assertNotNull(report);
        assertEquals(500L, report.getId());
        assertEquals(2, report.getTotalCount());
        assertEquals(1, report.getMatchedCount());
        assertEquals(1, report.getMismatchCount());
        assertEquals(ReconciliationReportStatus.MISMATCH_FOUND, report.getStatus());
        assertEquals(2, report.getItems().size());
    }

    @Test
    @DisplayName("API-108: Điều chỉnh dòng lệch tạo bút toán điều chỉnh sổ cái và chuyển báo cáo sang RESOLVED")
    void adjustMismatchItem_Success() {
        ReconciliationReport parentReport = ReconciliationReport.builder()
                .id(500L)
                .reportDate(LocalDate.of(2026, 11, 10))
                .status(ReconciliationReportStatus.MISMATCH_FOUND)
                .build();

        ReconciliationItem item = ReconciliationItem.builder()
                .id(77L)
                .report(parentReport)
                .txnRef("TOPUP_MISSING_002")
                .kind("WALLET_TOPUP")
                .internalAmount(0L)
                .vnpayAmount(200_000L)
                .status(ReconciliationItemStatus.MISMATCH)
                .discrepancyType(ReconciliationDiscrepancyType.MISSING_LEDGER)
                .build();

        LedgerTransaction adjTxn = LedgerTransaction.builder()
                .id(888L)
                .transactionCode("LTX-RECON-ADJ-TEST01")
                .amount(200_000L)
                .build();

        when(itemRepository.findById(77L)).thenReturn(Optional.of(item));
        when(ledgerService.recordReconciliationAdjustment(
                eq(77L), eq("TOPUP_MISSING_002"), eq("VNPAY_CLEARING"), eq("USER_WALLET_10"), eq(200_000L), anyString(), eq(1L)))
                .thenReturn(adjTxn);
        when(itemRepository.save(any(ReconciliationItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(itemRepository.countByReportIdAndStatus(500L, ReconciliationItemStatus.MISMATCH)).thenReturn(0L);

        ReconciliationAdjustRequest req = ReconciliationAdjustRequest.builder()
                .debitAccount("VNPAY_CLEARING")
                .creditAccount("USER_WALLET_10")
                .amount(200_000L)
                .note("Bổ sung bút toán nạp ví bị thiếu")
                .build();

        ReconciliationItemResponse resp = reconciliationService.adjustMismatchItem(77L, req, 1L);

        assertEquals(ReconciliationItemStatus.ADJUSTED, resp.getStatus());
        assertEquals("LTX-RECON-ADJ-TEST01", resp.getAdjustmentTxnCode());
        assertEquals(ReconciliationReportStatus.RESOLVED, parentReport.getStatus());
        verify(reportRepository, times(1)).save(parentReport);
    }

    @Test
    @DisplayName("API-108: Từ chối điều chỉnh khi dòng đã ở trạng thái MATCHED hoặc ADJUSTED")
    void adjustMismatchItem_AlreadyAdjusted_ThrowsConflict() {
        ReconciliationItem item = ReconciliationItem.builder()
                .id(78L)
                .status(ReconciliationItemStatus.ADJUSTED)
                .build();

        when(itemRepository.findById(78L)).thenReturn(Optional.of(item));

        AppException ex = assertThrows(AppException.class, () ->
                reconciliationService.adjustMismatchItem(78L, new ReconciliationAdjustRequest("A", "B", 100L, "Note"), 1L));

        assertEquals(PaymentErrorCode.RECONCILIATION_ITEM_ALREADY_ADJUSTED, ex.getErrorCode());
    }

    @Test
    @DisplayName("API-109: Kiểm tra bất biến sổ cái kép trả về balanced = true khi Tổng Nợ == Tổng Có")
    void checkLedgerIntegrity_Balanced() {
        LedgerTransaction txn = LedgerTransaction.builder()
                .id(1L)
                .transactionCode("LTX-001")
                .amount(150_000L)
                .build();
        txn.addEntry(LedgerEntry.builder().accountCode("VNPAY_CLEARING").debit(150_000L).credit(0L).build());
        txn.addEntry(LedgerEntry.builder().accountCode("USER_WALLET_10").debit(0L).credit(150_000L).build());

        when(ledgerTransactionRepository.findAll()).thenReturn(List.of(txn));

        LedgerIntegrityResponse integrity = reconciliationService.checkLedgerIntegrity();

        assertTrue(integrity.isBalanced());
        assertEquals(1, integrity.getTotalTransactions());
        assertEquals(2, integrity.getTotalEntries());
        assertEquals(150_000L, integrity.getTotalDebit());
        assertEquals(150_000L, integrity.getTotalCredit());
        assertEquals(0L, integrity.getDiscrepancyAmount());
        assertEquals(0, integrity.getUnbalancedTransactionCount());
    }

    @Test
    @DisplayName("API-107: Chạy đối soát lại trong cùng ngày cập nhật báo cáo hiện có và giữ nguyên dòng đã ADJUSTED (không sinh thêm lệch)")
    void runReconciliation_SameDateUpsertsAndPreservesAdjustedItems() {
        LocalDate targetDate = LocalDate.of(2026, 11, 10);

        PaymentTransaction adjustedPtx = PaymentTransaction.builder()
                .id(2L)
                .txnRef("TOPUP_MISSING_002")
                .purpose(PaymentPurpose.WALLET_TOPUP)
                .amount(BigDecimal.valueOf(200_000L))
                .status(PaymentStatus.SUCCESS)
                .createdAt(Instant.now())
                .build();

        ReconciliationReport existingReport = ReconciliationReport.builder()
                .id(500L)
                .reportDate(targetDate)
                .status(ReconciliationReportStatus.RESOLVED)
                .build();
        existingReport.addItem(ReconciliationItem.builder()
                .id(77L)
                .txnRef("TOPUP_MISSING_002")
                .kind("WALLET_TOPUP")
                .internalAmount(0L)
                .vnpayAmount(200_000L)
                .status(ReconciliationItemStatus.ADJUSTED)
                .discrepancyType(ReconciliationDiscrepancyType.MISSING_LEDGER)
                .adjustmentTxnCode("LTX-RECON-ADJ-001")
                .note("Đã xử lý")
                .build());

        when(reportRepository.findFirstByReportDateOrderByCreatedAtDesc(targetDate))
                .thenReturn(Optional.of(existingReport));
        when(paymentTransactionRepository.findByCreatedAtBetweenOrderByCreatedAtAsc(any(), any()))
                .thenReturn(List.of(adjustedPtx));
        when(ledgerTransactionRepository.findByCreatedAtBetweenOrderByCreatedAtAsc(any(), any()))
                .thenReturn(List.of());
        when(reportRepository.save(any(ReconciliationReport.class))).thenAnswer(inv -> inv.getArgument(0));

        ReconciliationReportResponse resp = reconciliationService.runReconciliation(targetDate, 99L);

        assertEquals(500L, resp.getId());
        assertEquals(1, resp.getTotalCount());
        assertEquals(0, resp.getMismatchCount());
        assertEquals(ReconciliationReportStatus.RESOLVED, resp.getStatus());
        assertEquals(1, resp.getItems().size());
        assertEquals(ReconciliationItemStatus.ADJUSTED, resp.getItems().get(0).getStatus());
    }
}
