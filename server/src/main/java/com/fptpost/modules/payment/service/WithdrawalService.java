package com.fptpost.modules.payment.service;

import com.fptpost.common.exception.AppException;
import com.fptpost.common.response.PageResponse;
import com.fptpost.modules.payment.dto.WithdrawalCreationRequest;
import com.fptpost.modules.payment.dto.WithdrawalResponse;
import com.fptpost.modules.payment.exception.PaymentErrorCode;
import com.fptpost.modules.payment.model.Wallet;
import com.fptpost.modules.payment.model.WithdrawalRequest;
import com.fptpost.modules.payment.model.WithdrawalStatus;
import com.fptpost.modules.payment.repository.WalletRepository;
import com.fptpost.modules.payment.repository.WithdrawalRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Service xử lý nghiệp vụ rút tiền từ ví về tài khoản ngân hàng (UC-40).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WithdrawalService {

    private static final long MIN_WITHDRAWAL = 50_000L;
    private static final long WITHDRAWAL_FEE = 0L;

    private final WithdrawalRequestRepository withdrawalRequestRepository;
    private final WalletRepository walletRepository;
    private final LedgerService ledgerService;

    /**
     * Khởi tạo yêu cầu rút tiền từ ví (UC-40, API-097).
     * Áp dụng Pessimistic Write Lock chuyển dịch số dư available -> pendingWithdraw và ghi sổ cái.
     */
    @Transactional
    public WithdrawalResponse createWithdrawalRequest(Long userId, WithdrawalCreationRequest request) {
        long amount = request.getAmount() != null ? request.getAmount() : 0L;
        if (amount < MIN_WITHDRAWAL) {
            log.warn("Withdrawal rejected: amount {} < min {}", amount, MIN_WITHDRAWAL);
            throw new AppException(PaymentErrorCode.WITHDRAWAL_AMOUNT_INVALID);
        }

        // Khóa bi quan (Pessimistic Write Lock) ngăn chặn race condition rút tiền đồng thời
        Wallet wallet = walletRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new AppException(PaymentErrorCode.WALLET_NOT_FOUND));

        long available = wallet.getAvailable() != null ? wallet.getAvailable() : 0L;
        long totalDeduction = amount + WITHDRAWAL_FEE;

        if (available < totalDeduction) {
            log.warn("Withdrawal rejected: insufficient available balance (user={}, available={}, required={})",
                    userId, available, totalDeduction);
            throw new AppException(PaymentErrorCode.WALLET_INSUFFICIENT_BALANCE);
        }

        // Chuyển dịch số dư nguyên tử (ACID Transaction): available -> pendingWithdraw
        wallet.setAvailable(available - totalDeduction);
        long currentPending = wallet.getPendingWithdraw() != null ? wallet.getPendingWithdraw() : 0L;
        wallet.setPendingWithdraw(currentPending + amount);
        walletRepository.save(wallet);

        // Sinh mã yêu cầu rút: WDR-YYYYMMDD-XXXXXXXX
        String dateStr = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String randomSuffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String requestCode = "WDR-" + dateStr + "-" + randomSuffix;

        WithdrawalRequest withdrawal = WithdrawalRequest.builder()
                .requestCode(requestCode)
                .userId(userId)
                .amount(amount)
                .feeAmount(WITHDRAWAL_FEE)
                .bankName(request.getBankName().trim())
                .accountNumber(request.getAccountNumber().trim())
                .accountHolderName(request.getAccountHolderName().trim().toUpperCase())
                .status(WithdrawalStatus.PENDING_REVIEW)
                .build();

        WithdrawalRequest saved = withdrawalRequestRepository.save(withdrawal);

        // Hạch toán sổ cái kép
        ledgerService.recordWithdrawalRequest(userId, amount, requestCode);

        log.info("Successfully created withdrawal request {} for userId={}, amount={}", requestCode, userId, amount);
        return mapToResponse(saved);
    }

    /**
     * Hủy yêu cầu rút tiền đang ở trạng thái chờ duyệt (UC-40, API-099).
     * Hoàn trả số dư: pendingWithdraw -> available và ghi bút toán hoàn tác sổ cái.
     */
    @Transactional
    public WithdrawalResponse cancelWithdrawalRequest(Long userId, Long withdrawalId) {
        WithdrawalRequest wdr = withdrawalRequestRepository.findById(withdrawalId)
                .orElseThrow(() -> new AppException(PaymentErrorCode.WITHDRAWAL_REQUEST_NOT_FOUND));

        if (!wdr.getUserId().equals(userId)) {
            log.warn("Withdrawal cancel forbidden: user {} does not own request {}", userId, withdrawalId);
            throw new AppException(PaymentErrorCode.WITHDRAWAL_ACCESS_DENIED);
        }

        if (wdr.getStatus() != WithdrawalStatus.PENDING_REVIEW) {
            log.warn("Withdrawal cancel rejected: request {} status is {}", withdrawalId, wdr.getStatus());
            throw new AppException(PaymentErrorCode.WITHDRAWAL_CANNOT_CANCEL);
        }

        // Khóa bi quan hoàn trả số dư ví: pendingWithdraw -> available
        Wallet wallet = walletRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new AppException(PaymentErrorCode.WALLET_NOT_FOUND));

        long pending = wallet.getPendingWithdraw() != null ? wallet.getPendingWithdraw() : 0L;
        long available = wallet.getAvailable() != null ? wallet.getAvailable() : 0L;
        long amount = wdr.getAmount();

        wallet.setPendingWithdraw(Math.max(0L, pending - amount));
        wallet.setAvailable(available + amount);
        walletRepository.save(wallet);

        // Cập nhật trạng thái
        wdr.setStatus(WithdrawalStatus.CANCELLED);
        WithdrawalRequest saved = withdrawalRequestRepository.save(wdr);

        // Ghi sổ cái hoàn trả tiền về ví
        ledgerService.recordWithdrawalCancellation(userId, amount, wdr.getRequestCode());

        log.info("Successfully cancelled withdrawal request {} for userId={}, returned {} to wallet",
                wdr.getRequestCode(), userId, amount);
        return mapToResponse(saved);
    }

    /**
     * Lấy danh sách phân trang các yêu cầu rút tiền của tài khoản hiện tại (UC-40, API-098).
     */
    @Transactional(readOnly = true)
    public PageResponse<WithdrawalResponse> getMyWithdrawals(Long userId, int page, int size) {
        int targetPage = Math.max(0, page);
        int targetSize = size > 0 && size <= 100 ? size : 10;
        Pageable pageable = PageRequest.of(targetPage, targetSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<WithdrawalRequest> pageResult = withdrawalRequestRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);

        return new PageResponse<>(
                pageResult.getContent().stream().map(this::mapToResponse).toList(),
                pageResult.getNumber(),
                pageResult.getSize(),
                pageResult.getTotalElements(),
                pageResult.getTotalPages(),
                pageResult.isFirst(),
                pageResult.isLast()
        );
    }

    /**
     * Xem chi tiết một yêu cầu rút tiền (UC-40).
     */
    @Transactional(readOnly = true)
    public WithdrawalResponse getWithdrawalById(Long userId, Long withdrawalId) {
        WithdrawalRequest wdr = withdrawalRequestRepository.findById(withdrawalId)
                .orElseThrow(() -> new AppException(PaymentErrorCode.WITHDRAWAL_REQUEST_NOT_FOUND));

        if (!wdr.getUserId().equals(userId)) {
            throw new AppException(PaymentErrorCode.WITHDRAWAL_ACCESS_DENIED);
        }

        return mapToResponse(wdr);
    }

    public WithdrawalResponse mapToResponse(WithdrawalRequest entity) {
        long fee = entity.getFeeAmount() != null ? entity.getFeeAmount() : 0L;
        long netAmount = Math.max(0L, entity.getAmount() - fee);

        return WithdrawalResponse.builder()
                .id(entity.getId())
                .requestCode(entity.getRequestCode())
                .userId(entity.getUserId())
                .amount(entity.getAmount())
                .feeAmount(fee)
                .netAmount(netAmount)
                .bankName(entity.getBankName())
                .accountNumber(entity.getAccountNumber())
                .accountHolderName(entity.getAccountHolderName())
                .status(entity.getStatus())
                .rejectionReason(entity.getRejectionReason())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
