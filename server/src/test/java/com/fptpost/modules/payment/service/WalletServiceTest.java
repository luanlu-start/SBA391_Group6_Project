package com.fptpost.modules.payment.service;

import com.fptpost.common.exception.AppException;
import com.fptpost.modules.payment.config.VNPayConfig;
import com.fptpost.modules.payment.dto.PaymentCreationResponse;
import com.fptpost.modules.payment.dto.WalletResponse;
import com.fptpost.modules.payment.dto.WalletTopupRequest;
import com.fptpost.modules.payment.exception.PaymentErrorCode;
import com.fptpost.modules.payment.model.PaymentTransaction;
import com.fptpost.modules.payment.model.Wallet;
import com.fptpost.modules.payment.repository.PaymentTransactionRepository;
import com.fptpost.modules.payment.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WalletService Unit Tests (UC-34 & UC-38)")
class WalletServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    @Mock
    private VNPayConfig vnPayConfig;

    @InjectMocks
    private WalletService walletService;

    private static final Long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        lenient().when(vnPayConfig.getVersion()).thenReturn("2.1.0");
        lenient().when(vnPayConfig.getCommand()).thenReturn("pay");
        lenient().when(vnPayConfig.getVnp_TmnCode()).thenReturn("B91MXQ5Y");
        lenient().when(vnPayConfig.getVnp_HashSecret()).thenReturn("HDMKJXGPKGDZUQJDDBEFKXEIXZYWSNCM");
        lenient().when(vnPayConfig.getCurrCode()).thenReturn("VND");
        lenient().when(vnPayConfig.getOrderType()).thenReturn("other");
        lenient().when(vnPayConfig.getLocale()).thenReturn("vn");
        lenient().when(vnPayConfig.getVnp_ReturnUrl()).thenReturn("http://localhost:8080/api/payment/vnpay-return");
        lenient().when(vnPayConfig.getVnp_PayUrl()).thenReturn("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        lenient().when(vnPayConfig.getTimeoutMinutes()).thenReturn(15);
    }

    @Test
    @DisplayName("getWallet should return wallet info when wallet exists")
    void testGetWallet_WhenExists() {
        Wallet wallet = Wallet.builder()
                .id(1L)
                .userId(USER_ID)
                .available(500_000L)
                .escrowHeld(200_000L)
                .pendingWithdraw(0L)
                .build();
        when(walletRepository.findByUserId(USER_ID)).thenReturn(Optional.of(wallet));

        WalletResponse response = walletService.getWallet(USER_ID);

        assertNotNull(response);
        assertEquals(500_000L, response.getAvailable());
        assertEquals(200_000L, response.getEscrowHeld());
        assertEquals(700_000L, response.getTotalBalance());
    }

    @Test
    @DisplayName("getWallet should return default 0 balance when wallet does not exist")
    void testGetWallet_WhenNotExists() {
        when(walletRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        WalletResponse response = walletService.getWallet(USER_ID);

        assertNotNull(response);
        assertEquals(0L, response.getAvailable());
        assertEquals(0L, response.getTotalBalance());
    }

    @Test
    @DisplayName("createTopupUrl should throw exception when amount < 20,000 VND [PRM-056]")
    void testCreateTopupUrl_AmountTooLow() {
        WalletTopupRequest request = WalletTopupRequest.builder()
                .amount(BigDecimal.valueOf(10_000))
                .build();

        AppException ex = assertThrows(AppException.class, () ->
                walletService.createTopupUrl(USER_ID, request, new MockHttpServletRequest()));
        assertEquals(PaymentErrorCode.WALLET_TOPUP_AMOUNT_INVALID, ex.getErrorCode());
    }

    @Test
    @DisplayName("createTopupUrl should throw exception when amount > 10,000,000 VND [PRM-057]")
    void testCreateTopupUrl_AmountTooHigh() {
        WalletTopupRequest request = WalletTopupRequest.builder()
                .amount(BigDecimal.valueOf(15_000_000))
                .build();

        AppException ex = assertThrows(AppException.class, () ->
                walletService.createTopupUrl(USER_ID, request, new MockHttpServletRequest()));
        assertEquals(PaymentErrorCode.WALLET_TOPUP_AMOUNT_INVALID, ex.getErrorCode());
    }

    @Test
    @DisplayName("createTopupUrl should throw exception when total balance would exceed 20,000,000 VND [PRM-058 / BR-R-050]")
    void testCreateTopupUrl_MaxBalanceExceeded() {
        Wallet wallet = Wallet.builder()
                .id(1L)
                .userId(USER_ID)
                .available(18_000_000L)
                .build();
        when(walletRepository.findByUserId(USER_ID)).thenReturn(Optional.of(wallet));

        WalletTopupRequest request = WalletTopupRequest.builder()
                .amount(BigDecimal.valueOf(5_000_000))
                .build();

        AppException ex = assertThrows(AppException.class, () ->
                walletService.createTopupUrl(USER_ID, request, new MockHttpServletRequest()));
        assertEquals(PaymentErrorCode.WALLET_MAX_BALANCE_EXCEEDED, ex.getErrorCode());
    }

    @Test
    @DisplayName("createTopupUrl should succeed and save PaymentTransaction with PENDING status")
    void testCreateTopupUrl_Success() {
        Wallet wallet = Wallet.builder()
                .id(1L)
                .userId(USER_ID)
                .available(1_000_000L)
                .build();
        when(walletRepository.findByUserId(USER_ID)).thenReturn(Optional.of(wallet));

        WalletTopupRequest request = WalletTopupRequest.builder()
                .amount(BigDecimal.valueOf(500_000))
                .bankCode("NCB")
                .language("vn")
                .build();

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.setRemoteAddr("127.0.0.1");

        PaymentCreationResponse response = walletService.createTopupUrl(USER_ID, request, httpRequest);

        assertNotNull(response);
        assertEquals("00", response.getCode());
        assertNotNull(response.getPaymentUrl());
        assertTrue(response.getPaymentUrl().contains("vnp_SecureHash"));
        verify(paymentTransactionRepository, times(1)).save(any(PaymentTransaction.class));
    }
}
