package com.fptpost.modules.payment.service;

import com.fptpost.common.exception.AppException;
import com.fptpost.modules.identity.enums.KycStatus;
import com.fptpost.modules.identity.model.KycRequest;
import com.fptpost.modules.identity.repository.KycRequestRepository;
import com.fptpost.modules.identity.repository.UserProfileRepository;
import com.fptpost.modules.identity.security.KycCrypto;
import com.fptpost.modules.payment.config.VNPayConfig;
import com.fptpost.modules.payment.dto.BankAccountCreationRequest;
import com.fptpost.modules.payment.dto.BankAccountResponse;
import com.fptpost.modules.payment.exception.PaymentErrorCode;
import com.fptpost.modules.payment.model.BankAccount;
import com.fptpost.modules.payment.repository.BankAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BankAccountService Unit Tests (UC-39)")
class BankAccountServiceTest {

    @Mock
    private BankAccountRepository bankAccountRepository;

    @Mock
    private KycRequestRepository kycRequestRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private KycCrypto kycCrypto;

    @Mock
    private VNPayConfig vnPayConfig;

    @InjectMocks
    private BankAccountService bankAccountService;

    private static final Long USER_ID = 10L;
    private KycRequest approvedKyc;

    @BeforeEach
    void setUp() {
        approvedKyc = KycRequest.builder()
                .id(1L)
                .fullName("Lê Nhựt Huy")
                .status(KycStatus.APPROVED)
                .submittedAt(Instant.now().minus(Duration.ofDays(2)))
                .build();
    }

    @Test
    @DisplayName("API-095: Liên kết tài khoản ngân hàng thành công khi KYC đã duyệt và tên khớp (không phân biệt dấu)")
    void createBankAccount_Success() {
        when(kycRequestRepository.findFirstByUserIdOrderBySubmittedAtDescIdDesc(USER_ID))
                .thenReturn(Optional.of(approvedKyc));
        when(bankAccountRepository.existsByUserIdAndBankCodeIgnoreCaseAndAccountNoHash(eq(USER_ID), eq("VCB"), anyString()))
                .thenReturn(false);
        when(bankAccountRepository.save(any(BankAccount.class))).thenAnswer(inv -> {
            BankAccount saved = inv.getArgument(0);
            saved.setId(50L);
            return saved;
        });

        BankAccountCreationRequest request = BankAccountCreationRequest.builder()
                .bankCode("vcb")
                .accountNumber("0123456789")
                .holderName("LE NHUT HUY")
                .build();

        BankAccountResponse response = bankAccountService.createBankAccount(USER_ID, request);

        assertNotNull(response);
        assertEquals(50L, response.getId());
        assertEquals("VCB", response.getBankCode());
        assertEquals("Vietcombank", response.getBankName());
        assertEquals("******6789", response.getMaskedAccountNumber());
        assertEquals("6789", response.getAccountLast4());
        assertEquals("LE NHUT HUY", response.getHolderName());
        assertFalse(response.isReadyForWithdrawal());
        assertNotNull(response.getCooldownUntil());
        assertTrue(response.getCooldownUntil().isAfter(Instant.now().plus(Duration.ofHours(23))));
    }

    @Test
    @DisplayName("API-095: Từ chối liên kết ngân hàng khi chưa duyệt KYC (BR-R-013)")
    void createBankAccount_KycRequired() {
        KycRequest pendingKyc = KycRequest.builder()
                .id(2L)
                .fullName("Lê Nhựt Huy")
                .status(KycStatus.PENDING)
                .build();
        when(kycRequestRepository.findFirstByUserIdOrderBySubmittedAtDescIdDesc(USER_ID))
                .thenReturn(Optional.of(pendingKyc));

        BankAccountCreationRequest request = BankAccountCreationRequest.builder()
                .bankCode("VCB")
                .accountNumber("0123456789")
                .holderName("LE NHUT HUY")
                .build();

        AppException ex = assertThrows(AppException.class, () ->
                bankAccountService.createBankAccount(USER_ID, request));

        assertEquals(PaymentErrorCode.BANK_ACCOUNT_KYC_REQUIRED, ex.getErrorCode());
        verify(bankAccountRepository, never()).save(any());
    }

    @Test
    @DisplayName("API-095 A1: Từ chối liên kết ngân hàng khi tên chủ tài khoản không khớp tên KYC (BR-R-058)")
    void createBankAccount_HolderMismatch() {
        when(kycRequestRepository.findFirstByUserIdOrderBySubmittedAtDescIdDesc(USER_ID))
                .thenReturn(Optional.of(approvedKyc));

        BankAccountCreationRequest request = BankAccountCreationRequest.builder()
                .bankCode("TCB")
                .accountNumber("9876543210")
                .holderName("NGUYEN VAN KHAC")
                .build();

        AppException ex = assertThrows(AppException.class, () ->
                bankAccountService.createBankAccount(USER_ID, request));

        assertEquals(PaymentErrorCode.BANK_ACCOUNT_HOLDER_MISMATCH, ex.getErrorCode());
        verify(bankAccountRepository, never()).save(any());
    }

    @Test
    @DisplayName("API-095: Từ chối khi số tài khoản đã tồn tại trong danh sách của người dùng")
    void createBankAccount_Duplicated() {
        when(kycRequestRepository.findFirstByUserIdOrderBySubmittedAtDescIdDesc(USER_ID))
                .thenReturn(Optional.of(approvedKyc));
        when(bankAccountRepository.existsByUserIdAndBankCodeIgnoreCaseAndAccountNoHash(eq(USER_ID), eq("MB"), anyString()))
                .thenReturn(true);

        BankAccountCreationRequest request = BankAccountCreationRequest.builder()
                .bankCode("MB")
                .accountNumber("0123456789")
                .holderName("Le Nhut Huy")
                .build();

        AppException ex = assertThrows(AppException.class, () ->
                bankAccountService.createBankAccount(USER_ID, request));

        assertEquals(PaymentErrorCode.BANK_ACCOUNT_DUPLICATED, ex.getErrorCode());
    }

    @Test
    @DisplayName("UC-39 A2 / BR-R-058: Chặn rút tiền vào tài khoản đang trong thời gian chờ 24 giờ")
    void resolveAndValidateForWithdrawal_CooldownActive() {
        BankAccount coolingAccount = BankAccount.builder()
                .id(77L)
                .userId(USER_ID)
                .bankCode("VCB")
                .bankName("Vietcombank")
                .accountNoEnc("ENC")
                .accountLast4("6789")
                .holderName("LE NHUT HUY")
                .cooldownUntil(Instant.now().plus(Duration.ofHours(12)))
                .build();

        when(bankAccountRepository.findByIdAndUserId(77L, USER_ID))
                .thenReturn(Optional.of(coolingAccount));

        AppException ex = assertThrows(AppException.class, () ->
                bankAccountService.resolveAndValidateForWithdrawal(USER_ID, 77L));

        assertEquals(PaymentErrorCode.BANK_ACCOUNT_COOLDOWN_ACTIVE, ex.getErrorCode());
    }

    @Test
    @DisplayName("UC-39: Cho phép rút tiền khi tài khoản ngân hàng đã hết thời gian chờ 24 giờ")
    void resolveAndValidateForWithdrawal_Ready() {
        String encrypted = bankAccountService.encryptAccountNumber("0123456789");
        BankAccount readyAccount = BankAccount.builder()
                .id(88L)
                .userId(USER_ID)
                .bankCode("VCB")
                .bankName("Vietcombank")
                .accountNoEnc(encrypted)
                .accountLast4("6789")
                .holderName("LE NHUT HUY")
                .cooldownUntil(Instant.now().minus(Duration.ofMinutes(5)))
                .build();

        when(bankAccountRepository.findByIdAndUserId(88L, USER_ID))
                .thenReturn(Optional.of(readyAccount));

        BankAccountService.ResolvedBankDetails details =
                bankAccountService.resolveAndValidateForWithdrawal(USER_ID, 88L);

        assertEquals("Vietcombank", details.bankName());
        assertEquals("0123456789", details.accountNumber());
        assertEquals("LE NHUT HUY", details.holderName());
    }

    @Test
    @DisplayName("API-094 & API-096: Lấy danh sách tài khoản (che số) và xóa tài khoản ngân hàng")
    void listAndDeleteBankAccounts_Success() {
        BankAccount acc = BankAccount.builder()
                .id(99L)
                .userId(USER_ID)
                .bankCode("ACB")
                .bankName("ACB")
                .accountNoEnc("ENC")
                .accountLast4("4321")
                .holderName("LE NHUT HUY")
                .cooldownUntil(Instant.now().minus(Duration.ofHours(1)))
                .createdAt(Instant.now().minus(Duration.ofHours(25)))
                .build();

        when(bankAccountRepository.findByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of(acc));
        when(bankAccountRepository.findByIdAndUserId(99L, USER_ID)).thenReturn(Optional.of(acc));

        List<BankAccountResponse> list = bankAccountService.getMyBankAccounts(USER_ID);
        assertEquals(1, list.size());
        assertEquals("******4321", list.get(0).getMaskedAccountNumber());
        assertTrue(list.get(0).isReadyForWithdrawal());

        bankAccountService.deleteBankAccount(USER_ID, 99L);
        verify(bankAccountRepository, times(1)).delete(acc);
    }
}
