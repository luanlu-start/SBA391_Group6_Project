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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WithdrawalService Unit Tests (UC-40)")
class WithdrawalServiceTest {

    @Mock
    private WithdrawalRequestRepository withdrawalRequestRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private LedgerService ledgerService;

    @InjectMocks
    private WithdrawalService withdrawalService;

    private static final Long USER_ID = 1L;
    private Wallet testWallet;
    private WithdrawalCreationRequest creationRequest;

    @BeforeEach
    void setUp() {
        testWallet = Wallet.builder()
                .id(10L)
                .userId(USER_ID)
                .available(500_000L)
                .escrowHeld(0L)
                .pendingWithdraw(0L)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        creationRequest = new WithdrawalCreationRequest();
        creationRequest.setAmount(100_000L);
        creationRequest.setBankName("Vietcombank");
        creationRequest.setAccountNumber("1234567890");
        creationRequest.setAccountHolderName("NGUYEN VAN A");
    }

    @Test
    @DisplayName("Tạo yêu cầu rút tiền thành công - Số dư khả dụng giảm, pendingWithdraw tăng, hạch toán sổ cái")
    void createWithdrawalRequest_Success() {
        when(walletRepository.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.of(testWallet));
        when(withdrawalRequestRepository.save(any(WithdrawalRequest.class))).thenAnswer(invocation -> {
            WithdrawalRequest entity = invocation.getArgument(0);
            entity.setId(100L);
            entity.setCreatedAt(Instant.now());
            entity.setUpdatedAt(Instant.now());
            return entity;
        });

        WithdrawalResponse response = withdrawalService.createWithdrawalRequest(USER_ID, creationRequest);

        assertNotNull(response);
        assertEquals(100L, response.getId());
        assertEquals(100_000L, response.getAmount());
        assertEquals(0L, response.getFeeAmount());
        assertEquals(WithdrawalStatus.PENDING_REVIEW, response.getStatus());
        assertEquals("Vietcombank", response.getBankName());
        assertEquals("1234567890", response.getAccountNumber());
        assertEquals("NGUYEN VAN A", response.getAccountHolderName());
        assertTrue(response.getRequestCode().startsWith("WDR-"));

        // Kiểm tra biến động số dư ví
        assertEquals(400_000L, testWallet.getAvailable());
        assertEquals(100_000L, testWallet.getPendingWithdraw());
        verify(walletRepository, times(1)).save(testWallet);

        // Kiểm tra hạch toán sổ cái kế toán
        verify(ledgerService, times(1)).recordWithdrawalRequest(eq(USER_ID), eq(100_000L), eq(response.getRequestCode()));
    }

    @Test
    @DisplayName("Tạo yêu cầu rút tiền thất bại khi số tiền rút nhỏ hơn mức tối thiểu 50.000 VND")
    void createWithdrawalRequest_AmountBelowMin() {
        creationRequest.setAmount(30_000L);

        AppException ex = assertThrows(AppException.class, () ->
                withdrawalService.createWithdrawalRequest(USER_ID, creationRequest));

        assertEquals(PaymentErrorCode.WITHDRAWAL_AMOUNT_INVALID, ex.getErrorCode());
        verify(walletRepository, never()).findByUserIdForUpdate(any());
        verify(withdrawalRequestRepository, never()).save(any());
        verify(ledgerService, never()).recordWithdrawalRequest(any(), any(), any());
    }

    @Test
    @DisplayName("Tạo yêu cầu rút tiền thất bại khi số dư khả dụng không đủ")
    void createWithdrawalRequest_InsufficientBalance() {
        testWallet.setAvailable(80_000L);
        when(walletRepository.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.of(testWallet));

        AppException ex = assertThrows(AppException.class, () ->
                withdrawalService.createWithdrawalRequest(USER_ID, creationRequest));

        assertEquals(PaymentErrorCode.WALLET_INSUFFICIENT_BALANCE, ex.getErrorCode());
        // Số dư không bị biến động
        assertEquals(80_000L, testWallet.getAvailable());
        assertEquals(0L, testWallet.getPendingWithdraw());
        verify(walletRepository, never()).save(testWallet);
        verify(withdrawalRequestRepository, never()).save(any());
        verify(ledgerService, never()).recordWithdrawalRequest(any(), any(), any());
    }

    @Test
    @DisplayName("Tạo yêu cầu rút tiền thất bại khi không tìm thấy ví người dùng")
    void createWithdrawalRequest_WalletNotFound() {
        when(walletRepository.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () ->
                withdrawalService.createWithdrawalRequest(USER_ID, creationRequest));

        assertEquals(PaymentErrorCode.WALLET_NOT_FOUND, ex.getErrorCode());
        verify(withdrawalRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("Hủy yêu cầu rút tiền thành công - Hoàn trả pendingWithdraw -> available và ghi sổ cái")
    void cancelWithdrawalRequest_Success() {
        WithdrawalRequest existingWdr = WithdrawalRequest.builder()
                .id(200L)
                .requestCode("WDR-20261024-ABC12345")
                .userId(USER_ID)
                .amount(150_000L)
                .feeAmount(0L)
                .bankName("Techcombank")
                .accountNumber("987654321")
                .accountHolderName("TRAN THI B")
                .status(WithdrawalStatus.PENDING_REVIEW)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        testWallet.setAvailable(300_000L);
        testWallet.setPendingWithdraw(150_000L);

        when(withdrawalRequestRepository.findById(200L)).thenReturn(Optional.of(existingWdr));
        when(walletRepository.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.of(testWallet));
        when(withdrawalRequestRepository.save(any(WithdrawalRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WithdrawalResponse response = withdrawalService.cancelWithdrawalRequest(USER_ID, 200L);

        assertNotNull(response);
        assertEquals(WithdrawalStatus.CANCELLED, response.getStatus());

        // Kiểm tra số dư ví được hoàn trả
        assertEquals(450_000L, testWallet.getAvailable());
        assertEquals(0L, testWallet.getPendingWithdraw());
        verify(walletRepository, times(1)).save(testWallet);

        // Kiểm tra hạch toán hoàn trả sổ cái
        verify(ledgerService, times(1)).recordWithdrawalCancellation(eq(USER_ID), eq(150_000L), eq("WDR-20261024-ABC12345"));
    }

    @Test
    @DisplayName("Hủy yêu cầu rút tiền thất bại khi yêu cầu không tồn tại")
    void cancelWithdrawalRequest_NotFound() {
        when(withdrawalRequestRepository.findById(999L)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () ->
                withdrawalService.cancelWithdrawalRequest(USER_ID, 999L));

        assertEquals(PaymentErrorCode.WITHDRAWAL_REQUEST_NOT_FOUND, ex.getErrorCode());
        verify(walletRepository, never()).findByUserIdForUpdate(any());
    }

    @Test
    @DisplayName("Hủy yêu cầu rút tiền thất bại khi không phải chủ sở hữu yêu cầu")
    void cancelWithdrawalRequest_AccessDenied() {
        WithdrawalRequest otherUserWdr = WithdrawalRequest.builder()
                .id(300L)
                .userId(99L) // Người dùng khác
                .amount(100_000L)
                .status(WithdrawalStatus.PENDING_REVIEW)
                .build();

        when(withdrawalRequestRepository.findById(300L)).thenReturn(Optional.of(otherUserWdr));

        AppException ex = assertThrows(AppException.class, () ->
                withdrawalService.cancelWithdrawalRequest(USER_ID, 300L));

        assertEquals(PaymentErrorCode.WITHDRAWAL_ACCESS_DENIED, ex.getErrorCode());
        verify(walletRepository, never()).findByUserIdForUpdate(any());
    }

    @Test
    @DisplayName("Hủy yêu cầu rút tiền thất bại khi yêu cầu không ở trạng thái PENDING_REVIEW")
    void cancelWithdrawalRequest_NotPendingReview() {
        WithdrawalRequest approvedWdr = WithdrawalRequest.builder()
                .id(400L)
                .userId(USER_ID)
                .amount(100_000L)
                .status(WithdrawalStatus.APPROVED)
                .build();

        when(withdrawalRequestRepository.findById(400L)).thenReturn(Optional.of(approvedWdr));

        AppException ex = assertThrows(AppException.class, () ->
                withdrawalService.cancelWithdrawalRequest(USER_ID, 400L));

        assertEquals(PaymentErrorCode.WITHDRAWAL_CANNOT_CANCEL, ex.getErrorCode());
        verify(walletRepository, never()).findByUserIdForUpdate(any());
    }

    @Test
    @DisplayName("Lấy danh sách yêu cầu rút tiền của tôi phân trang")
    void getMyWithdrawals_Success() {
        WithdrawalRequest wdr = WithdrawalRequest.builder()
                .id(1L)
                .requestCode("WDR-20261024-001")
                .userId(USER_ID)
                .amount(200_000L)
                .feeAmount(0L)
                .bankName("MB Bank")
                .accountNumber("11223344")
                .accountHolderName("NGUYEN VAN A")
                .status(WithdrawalStatus.PENDING_REVIEW)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Page<WithdrawalRequest> page = new PageImpl<>(List.of(wdr));
        when(withdrawalRequestRepository.findByUserIdOrderByCreatedAtDesc(eq(USER_ID), any(Pageable.class)))
                .thenReturn(page);

        PageResponse<WithdrawalResponse> result = withdrawalService.getMyWithdrawals(USER_ID, 0, 10);

        assertNotNull(result);
        assertEquals(1, result.content().size());
        assertEquals("WDR-20261024-001", result.content().get(0).getRequestCode());
        assertEquals(200_000L, result.content().get(0).getAmount());
    }

    @Test
    @DisplayName("Xem chi tiết yêu cầu rút tiền thành công")
    void getWithdrawalById_Success() {
        WithdrawalRequest wdr = WithdrawalRequest.builder()
                .id(1L)
                .requestCode("WDR-20261024-001")
                .userId(USER_ID)
                .amount(200_000L)
                .feeAmount(0L)
                .bankName("MB Bank")
                .accountNumber("11223344")
                .accountHolderName("NGUYEN VAN A")
                .status(WithdrawalStatus.PENDING_REVIEW)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(withdrawalRequestRepository.findById(1L)).thenReturn(Optional.of(wdr));

        WithdrawalResponse response = withdrawalService.getWithdrawalById(USER_ID, 1L);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("WDR-20261024-001", response.getRequestCode());
    }

    @Test
    @DisplayName("Xem chi tiết yêu cầu rút tiền của người khác bị chặn")
    void getWithdrawalById_AccessDenied() {
        WithdrawalRequest wdr = WithdrawalRequest.builder()
                .id(1L)
                .userId(2L)
                .amount(200_000L)
                .build();

        when(withdrawalRequestRepository.findById(1L)).thenReturn(Optional.of(wdr));

        AppException ex = assertThrows(AppException.class, () ->
                withdrawalService.getWithdrawalById(USER_ID, 1L));

        assertEquals(PaymentErrorCode.WITHDRAWAL_ACCESS_DENIED, ex.getErrorCode());
    }
}
