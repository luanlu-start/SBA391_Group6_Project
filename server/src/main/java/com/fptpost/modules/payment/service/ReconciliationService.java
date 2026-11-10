package com.fptpost.modules.payment.service;

import com.fptpost.common.exception.AppException;
import com.fptpost.common.response.PageResponse;
import com.fptpost.modules.payment.dto.LedgerIntegrityResponse;
import com.fptpost.modules.payment.dto.ReconciliationAdjustRequest;
import com.fptpost.modules.payment.dto.ReconciliationItemResponse;
import com.fptpost.modules.payment.dto.ReconciliationReportResponse;
import com.fptpost.modules.payment.exception.PaymentErrorCode;
import com.fptpost.modules.payment.model.LedgerEntry;
import com.fptpost.modules.payment.model.LedgerTransaction;
import com.fptpost.modules.payment.model.LedgerTransactionType;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service đối soát giao dịch hằng ngày và kiểm tra bất biến sổ cái (UC-43, BR-F-082, BR-F-083, BR-N-015, BR-N-039, BR-R-062).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationService {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final ReconciliationReportRepository reportRepository;
    private final ReconciliationItemRepository itemRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final LedgerTransactionRepository ledgerTransactionRepository;
    private final LedgerService ledgerService;

    /**
     * Danh sách báo cáo đối soát phân trang (UC-43, API-106).
     */
    @Transactional(readOnly = true)
    public PageResponse<ReconciliationReportResponse> getReports(int page, int size) {
        int targetPage = Math.max(0, page);
        int targetSize = size > 0 && size <= 100 ? size : 10;
        Pageable pageable = PageRequest.of(targetPage, targetSize);

        Page<ReconciliationReport> pageResult = reportRepository.findAllByOrderByReportDateDescCreatedAtDesc(pageable);

        List<ReconciliationReportResponse> content = pageResult.getContent()
                .stream()
                .map(r -> mapReportToResponse(r, true))
                .toList();

        return new PageResponse<>(
                content,
                pageResult.getNumber(),
                pageResult.getSize(),
                pageResult.getTotalElements(),
                pageResult.getTotalPages(),
                pageResult.isFirst(),
                pageResult.isLast()
        );
    }

    /**
     * Xem chi tiết báo cáo đối soát kèm danh sách dòng khớp/lệch (UC-43).
     */
    @Transactional(readOnly = true)
    public ReconciliationReportResponse getReportById(Long reportId) {
        ReconciliationReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new AppException(PaymentErrorCode.RECONCILIATION_REPORT_NOT_FOUND));
        return mapReportToResponse(report, true);
    }

    /**
     * Chạy đối soát giao dịch theo ngày (UC-43, API-107).
     * Đối chiếu giữa giao dịch cổng VNPay (PaymentTransaction) và bút toán sổ cái (LedgerTransaction).
     * Tuyệt đối KHÔNG tự động sửa số dư ví khi phát hiện lệch (BR-R-062).
     */
    @Transactional
    public ReconciliationReportResponse runReconciliation(LocalDate reportDate, Long actorUserId) {
        LocalDate targetDate = reportDate != null
                ? reportDate
                : LocalDate.now(VIETNAM_ZONE);

        ZonedDateTime startOfDay = targetDate.atStartOfDay(VIETNAM_ZONE);
        ZonedDateTime endOfDay = startOfDay.plusDays(1).minusNanos(1);
        Instant startInstant = startOfDay.toInstant();
        Instant endInstant = endOfDay.toInstant();

        List<PaymentTransaction> paymentTxns =
                paymentTransactionRepository.findByCreatedAtBetweenOrderByCreatedAtAsc(startInstant, endInstant);
        List<LedgerTransaction> ledgerTxns =
                ledgerTransactionRepository.findByCreatedAtBetweenOrderByCreatedAtAsc(startInstant, endInstant);

        // Gom nhóm các bút toán sổ cái liên quan đến cổng VNPay theo refId (txnRef)
        Map<String, LedgerTransaction> vnpayLedgerByRef = new HashMap<>();
        Map<String, LedgerTransaction> adjustedLedgerByRef = new HashMap<>();
        for (LedgerTransaction ltx : ledgerTxns) {
            if ("PAYMENT_TRANSACTION".equalsIgnoreCase(ltx.getRefType()) && ltx.getRefId() != null) {
                vnpayLedgerByRef.put(ltx.getRefId(), ltx);
            } else if (ltx.getType() == LedgerTransactionType.RECONCILIATION_ADJUSTMENT && ltx.getRefId() != null) {
                adjustedLedgerByRef.putIfAbsent(ltx.getRefId(), ltx);
            }
        }

        // Upsert 1 báo cáo theo từng ngày báo cáo để không nhân bản thêm dòng lệch khi chạy lại nhiều lần
        ReconciliationReport report = reportRepository.findFirstByReportDateOrderByCreatedAtDesc(targetDate)
                .orElseGet(() -> ReconciliationReport.builder()
                        .reportDate(targetDate)
                        .createdBy(actorUserId)
                        .totalCount(0)
                        .matchedCount(0)
                        .mismatchCount(0)
                        .status(ReconciliationReportStatus.MATCHED)
                        .build());

        Map<String, ReconciliationItem> keptAdjustedByRef = new HashMap<>();
        if (report.getItems() != null) {
            report.getItems().removeIf(item -> item.getStatus() != ReconciliationItemStatus.ADJUSTED);
            for (ReconciliationItem kept : report.getItems()) {
                if (kept.getTxnRef() != null) {
                    keptAdjustedByRef.put(kept.getTxnRef(), kept);
                }
            }
        }

        Map<String, ReconciliationItem> historicalAdjustedByRef = new HashMap<>();
        List<ReconciliationItem> priorAdjustedItems =
                itemRepository.findByReportReportDateAndStatus(targetDate, ReconciliationItemStatus.ADJUSTED);
        if (priorAdjustedItems != null) {
            for (ReconciliationItem prior : priorAdjustedItems) {
                if (prior.getTxnRef() != null) {
                    historicalAdjustedByRef.putIfAbsent(prior.getTxnRef(), prior);
                }
            }
        }

        Set<String> processedRefs = new HashSet<>();
        int matched = 0;
        int adjusted = 0;
        int mismatch = 0;

        for (PaymentTransaction ptx : paymentTxns) {
            String ref = ptx.getTxnRef();
            processedRefs.add(ref);
            long vnpAmount = ptx.getAmount() != null ? ptx.getAmount().longValue() : 0L;
            String kind = ptx.getPurpose() != null ? ptx.getPurpose().name() : "PAYMENT";
            LedgerTransaction ltx = vnpayLedgerByRef.get(ref);

            if (ptx.getStatus() == PaymentStatus.SUCCESS) {
                if (ltx == null) {
                    if (recordOrReuseAdjustment(report, keptAdjustedByRef, historicalAdjustedByRef, adjustedLedgerByRef,
                            ref, kind, 0L, vnpAmount, ReconciliationDiscrepancyType.MISSING_LEDGER)) {
                        adjusted++;
                    } else {
                        mismatch++;
                    }
                } else {
                    long internalAmt = ltx.getAmount() != null ? ltx.getAmount() : 0L;
                    if (internalAmt == vnpAmount) {
                        matched++;
                        report.addItem(ReconciliationItem.builder()
                                .txnRef(ref)
                                .kind(kind)
                                .internalAmount(internalAmt)
                                .vnpayAmount(vnpAmount)
                                .status(ReconciliationItemStatus.MATCHED)
                                .discrepancyType(ReconciliationDiscrepancyType.NONE)
                                .note("Khớp số tiền giữa VNPay và Sổ cái (" + ltx.getTransactionCode() + ")")
                                .build());
                    } else {
                        if (recordOrReuseAdjustment(report, keptAdjustedByRef, historicalAdjustedByRef, adjustedLedgerByRef,
                                ref, kind, internalAmt, vnpAmount, ReconciliationDiscrepancyType.AMOUNT_MISMATCH)) {
                            adjusted++;
                        } else {
                            mismatch++;
                        }
                    }
                }
            } else if (ptx.getStatus() == PaymentStatus.PENDING
                    && ptx.getExpiresAt() != null
                    && ptx.getExpiresAt().isBefore(Instant.now())) {
                if (ltx != null) {
                    long internalAmt = ltx.getAmount() != null ? ltx.getAmount() : 0L;
                    if (recordOrReuseAdjustment(report, keptAdjustedByRef, historicalAdjustedByRef, adjustedLedgerByRef,
                            ref, kind, internalAmt, 0L, ReconciliationDiscrepancyType.MISSING_PAYMENT)) {
                        adjusted++;
                    } else {
                        mismatch++;
                    }
                } else {
                    if (recordOrReuseAdjustment(report, keptAdjustedByRef, historicalAdjustedByRef, adjustedLedgerByRef,
                            ref, kind, 0L, vnpAmount, ReconciliationDiscrepancyType.STALE_PENDING)) {
                        adjusted++;
                    } else {
                        mismatch++;
                    }
                }
            } else if (ltx != null) {
                long internalAmt = ltx.getAmount() != null ? ltx.getAmount() : 0L;
                if (recordOrReuseAdjustment(report, keptAdjustedByRef, historicalAdjustedByRef, adjustedLedgerByRef,
                        ref, kind, internalAmt, 0L, ReconciliationDiscrepancyType.MISSING_PAYMENT)) {
                    adjusted++;
                } else {
                    mismatch++;
                }
            }
        }

        // Kiểm tra các bút toán sổ cái tham chiếu PAYMENT_TRANSACTION nhưng không có bản ghi PaymentTransaction trong ngày
        for (Map.Entry<String, LedgerTransaction> entry : vnpayLedgerByRef.entrySet()) {
            String ref = entry.getKey();
            if (!processedRefs.contains(ref)) {
                processedRefs.add(ref);
                LedgerTransaction ltx = entry.getValue();
                long internalAmt = ltx.getAmount() != null ? ltx.getAmount() : 0L;
                String kind = ltx.getType() != null ? ltx.getType().name() : "LEDGER_ONLY";
                if (recordOrReuseAdjustment(report, keptAdjustedByRef, historicalAdjustedByRef, adjustedLedgerByRef,
                        ref, kind, internalAmt, 0L, ReconciliationDiscrepancyType.MISSING_PAYMENT)) {
                    adjusted++;
                } else {
                    mismatch++;
                }
            }
        }

        for (String keptRef : keptAdjustedByRef.keySet()) {
            if (!processedRefs.contains(keptRef)) {
                adjusted++;
            }
        }

        int total = matched + adjusted + mismatch;
        report.setTotalCount(total);
        report.setMatchedCount(matched);
        report.setMismatchCount(mismatch);
        if (mismatch > 0) {
            report.setStatus(ReconciliationReportStatus.MISMATCH_FOUND);
        } else if (adjusted > 0) {
            report.setStatus(ReconciliationReportStatus.RESOLVED);
        } else {
            report.setStatus(ReconciliationReportStatus.MATCHED);
        }

        ReconciliationReport saved = reportRepository.save(report);
        log.info("Completed daily reconciliation for date={}: total={}, matched={}, adjusted={}, mismatch={}, status={}",
                targetDate, total, matched, adjusted, mismatch, saved.getStatus());

        return mapReportToResponse(saved, true);
    }

    private boolean recordOrReuseAdjustment(
            ReconciliationReport report,
            Map<String, ReconciliationItem> keptAdjustedByRef,
            Map<String, ReconciliationItem> historicalAdjustedByRef,
            Map<String, LedgerTransaction> adjustedLedgerByRef,
            String ref,
            String kind,
            long internalAmount,
            long vnpayAmount,
            ReconciliationDiscrepancyType discrepancyType) {
        if (keptAdjustedByRef.containsKey(ref)) {
            return true;
        }

        ReconciliationItem historical = historicalAdjustedByRef.get(ref);
        if (historical != null) {
            report.addItem(ReconciliationItem.builder()
                    .txnRef(ref)
                    .kind(kind)
                    .internalAmount(internalAmount)
                    .vnpayAmount(vnpayAmount)
                    .status(ReconciliationItemStatus.ADJUSTED)
                    .discrepancyType(discrepancyType)
                    .note(historical.getNote())
                    .adjustmentTxnCode(historical.getAdjustmentTxnCode())
                    .adjustedBy(historical.getAdjustedBy())
                    .adjustedAt(historical.getAdjustedAt())
                    .build());
            return true;
        }

        LedgerTransaction adjLedger = adjustedLedgerByRef.get(ref);
        if (adjLedger != null) {
            report.addItem(ReconciliationItem.builder()
                    .txnRef(ref)
                    .kind(kind)
                    .internalAmount(internalAmount)
                    .vnpayAmount(vnpayAmount)
                    .status(ReconciliationItemStatus.ADJUSTED)
                    .discrepancyType(discrepancyType)
                    .note(adjLedger.getDescription() != null ? adjLedger.getDescription() : "Đã tạo bút toán điều chỉnh đối soát")
                    .adjustmentTxnCode(adjLedger.getTransactionCode())
                    .adjustedBy(adjLedger.getCreatedBy())
                    .adjustedAt(adjLedger.getCreatedAt())
                    .build());
            return true;
        }

        String defaultNote = switch (discrepancyType) {
            case MISSING_LEDGER -> "Giao dịch VNPay thành công nhưng chưa có bút toán ghi sổ cái";
            case AMOUNT_MISMATCH -> "Lệch số tiền giữa VNPay (" + vnpayAmount + ") và Sổ cái (" + internalAmount + ")";
            case STALE_PENDING -> "Giao dịch VNPay treo PENDING quá thời hạn thanh toán";
            case MISSING_PAYMENT -> internalAmount > 0 && vnpayAmount == 0
                    ? "Có bút toán sổ cái nhưng VNPay chưa ghi nhận thành công"
                    : "Chênh lệch đối ứng VNPay";
            case NONE -> "";
        };

        report.addItem(ReconciliationItem.builder()
                .txnRef(ref)
                .kind(kind)
                .internalAmount(internalAmount)
                .vnpayAmount(vnpayAmount)
                .status(ReconciliationItemStatus.MISMATCH)
                .discrepancyType(discrepancyType)
                .note(defaultNote)
                .build());
        return false;
    }

    /**
     * Điều chỉnh dòng lệch bằng bút toán điều chỉnh sổ cái (UC-43, API-108, BR-F-083, BR-N-039).
     */
    @Transactional
    public ReconciliationItemResponse adjustMismatchItem(
            Long itemId,
            ReconciliationAdjustRequest request,
            Long adminUserId) {
        ReconciliationItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new AppException(PaymentErrorCode.RECONCILIATION_ITEM_NOT_FOUND));

        if (item.getStatus() != ReconciliationItemStatus.MISMATCH) {
            throw new AppException(PaymentErrorCode.RECONCILIATION_ITEM_ALREADY_ADJUSTED);
        }

        String debitAccount = request.getDebitAccount() != null ? request.getDebitAccount().trim().toUpperCase() : "";
        String creditAccount = request.getCreditAccount() != null ? request.getCreditAccount().trim().toUpperCase() : "";
        long amount = request.getAmount() != null ? request.getAmount() : 0L;
        String note = request.getNote() != null ? request.getNote().trim() : "";

        if (debitAccount.isEmpty() || creditAccount.isEmpty()
                || debitAccount.equals(creditAccount)
                || amount <= 0
                || note.isEmpty()) {
            throw new AppException(PaymentErrorCode.RECONCILIATION_ADJUSTMENT_INVALID);
        }

        LedgerTransaction adjTxn = ledgerService.recordReconciliationAdjustment(
                item.getId(),
                item.getTxnRef(),
                debitAccount,
                creditAccount,
                amount,
                note,
                adminUserId
        );

        item.setStatus(ReconciliationItemStatus.ADJUSTED);
        item.setNote(note);
        item.setAdjustmentTxnCode(adjTxn != null ? adjTxn.getTransactionCode() : "LTX-RECON-ADJ-" + itemId);
        item.setAdjustedBy(adminUserId);
        item.setAdjustedAt(Instant.now());
        ReconciliationItem savedItem = itemRepository.save(item);

        // Nếu tất cả dòng lệch trong báo cáo đã được xử lý, chuyển trạng thái báo cáo sang RESOLVED
        ReconciliationReport report = item.getReport();
        if (report != null && report.getId() != null) {
            long remainingMismatches = itemRepository.countByReportIdAndStatus(
                    report.getId(),
                    ReconciliationItemStatus.MISMATCH
            );
            if (remainingMismatches == 0) {
                report.setStatus(ReconciliationReportStatus.RESOLVED);
                reportRepository.save(report);
            }
        }

        return mapItemToResponse(savedItem);
    }

    /**
     * Kiểm tra tính toàn vẹn / bất biến sổ cái kép (UC-43, API-109, BR-N-039).
     * Đảm bảo tổng Debit == tổng Credit trên từng giao dịch và trên toàn bộ sổ cái.
     */
    @Transactional(readOnly = true)
    public LedgerIntegrityResponse checkLedgerIntegrity() {
        List<LedgerTransaction> allTransactions = ledgerTransactionRepository.findAll();

        long totalEntries = 0L;
        long totalDebit = 0L;
        long totalCredit = 0L;
        List<String> unbalancedCodes = new ArrayList<>();

        for (LedgerTransaction txn : allTransactions) {
            long txnDebit = 0L;
            long txnCredit = 0L;
            if (txn.getEntries() != null) {
                for (LedgerEntry entry : txn.getEntries()) {
                    totalEntries++;
                    long d = entry.getDebit() != null ? entry.getDebit() : 0L;
                    long c = entry.getCredit() != null ? entry.getCredit() : 0L;
                    txnDebit += d;
                    txnCredit += c;
                    totalDebit += d;
                    totalCredit += c;
                }
            }
            if (txnDebit != txnCredit || (txn.getAmount() != null && txnDebit != txn.getAmount())) {
                unbalancedCodes.add(txn.getTransactionCode());
            }
        }

        long discrepancy = Math.abs(totalDebit - totalCredit);
        boolean balanced = discrepancy == 0L && unbalancedCodes.isEmpty();

        return LedgerIntegrityResponse.builder()
                .balanced(balanced)
                .totalTransactions(allTransactions.size())
                .totalEntries(totalEntries)
                .totalDebit(totalDebit)
                .totalCredit(totalCredit)
                .discrepancyAmount(discrepancy)
                .unbalancedTransactionCount(unbalancedCodes.size())
                .unbalancedTransactionCodes(unbalancedCodes)
                .checkedAt(Instant.now())
                .message(balanced
                        ? "Sổ cái kép toàn vẹn tuyệt đối: Tổng Nợ bằng Tổng Có trên toàn hệ thống."
                        : "CẢNH BÁO: Phát hiện sai lệch bất biến sổ cái kép, cần Admin kiểm tra ngay!")
                .build();
    }

    public ReconciliationReportResponse mapReportToResponse(ReconciliationReport report, boolean includeItems) {
        List<ReconciliationItemResponse> itemDtos = includeItems && report.getItems() != null
                ? report.getItems().stream().map(this::mapItemToResponse).toList()
                : List.of();

        return ReconciliationReportResponse.builder()
                .id(report.getId())
                .reportDate(report.getReportDate())
                .totalCount(report.getTotalCount())
                .matchedCount(report.getMatchedCount())
                .mismatchCount(report.getMismatchCount())
                .status(report.getStatus())
                .createdBy(report.getCreatedBy())
                .createdAt(report.getCreatedAt())
                .updatedAt(report.getUpdatedAt())
                .items(itemDtos)
                .build();
    }

    public ReconciliationItemResponse mapItemToResponse(ReconciliationItem item) {
        long internalAmt = item.getInternalAmount() != null ? item.getInternalAmount() : 0L;
        long vnpayAmt = item.getVnpayAmount() != null ? item.getVnpayAmount() : 0L;

        return ReconciliationItemResponse.builder()
                .id(item.getId())
                .reportId(item.getReport() != null ? item.getReport().getId() : null)
                .txnRef(item.getTxnRef())
                .kind(item.getKind())
                .internalAmount(internalAmt)
                .vnpayAmount(vnpayAmt)
                .differenceAmount(vnpayAmt - internalAmt)
                .status(item.getStatus())
                .discrepancyType(item.getDiscrepancyType())
                .note(item.getNote())
                .adjustmentTxnCode(item.getAdjustmentTxnCode())
                .adjustedBy(item.getAdjustedBy())
                .adjustedAt(item.getAdjustedAt())
                .createdAt(item.getCreatedAt())
                .build();
    }
}
