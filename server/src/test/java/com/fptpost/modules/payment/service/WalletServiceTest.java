package com.fptpost.modules.payment.service;

import com.fptpost.common.exception.AppException;
import com.fptpost.modules.payment.config.VNPayConfig;
import com.fptpost.modules.payment.dto.PaymentCreationResponse;
import com.fptpost.modules.payment.dto.WalletResponse;
import com.fptpost.modules.payment.dto.WalletTopupRequest;
import com.fptpost.modules.payment.exception.PaymentErrorCode;
import com.fptpost.modules.payment.model.PaymentTransaction;
import com.fptpost.modules.payment.model.Wallet;
import com.fptpost.modules.payment.repository.LedgerEntryRepository;
import com.fptpost.modules.payment.repository.LedgerTransactionRepository;
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
import com.fptpost.common.response.PageResponse;
import com.fptpost.modules.payment.dto.WalletStatementResponse;
import com.fptpost.modules.payment.dto.WalletTransactionFilterRequest;
import com.fptpost.modules.payment.dto.WalletTransactionResponse;
import com.fptpost.modules.payment.model.LedgerEntry;
import com.fptpost.modules.payment.model.LedgerTransaction;
import com.fptpost.modules.payment.model.LedgerTransactionStatus;
import com.fptpost.modules.payment.model.LedgerTransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
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
    private LedgerTransactionRepository ledgerTransactionRepository;

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

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
        lenient().when(vnPayConfig.getVnp_HashSecret()).thenReturn("test-only-vnpay-hmac-key-not-for-real-payments");
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

    @Test
    @DisplayName("getTransactionHistory should return paged transactions with correct balance directions (UC-38, API-089)")
    void testGetTransactionHistory_Success() {
        LedgerTransaction topupTx = LedgerTransaction.builder()
                .id(1L)
                .transactionCode("LTX-TOPUP-1111")
                .type(LedgerTransactionType.WALLET_TOPUP)
                .status(LedgerTransactionStatus.COMPLETED)
                .amount(500_000L)
                .description("Nạp tiền ví")
                .createdAt(Instant.now())
                .build();
        topupTx.addEntry(LedgerEntry.builder()
                .accountCode("USER_WALLET_" + USER_ID)
                .credit(500_000L)
                .debit(0L)
                .build());

        LedgerTransaction escrowTx = LedgerTransaction.builder()
                .id(2L)
                .transactionCode("LTX-ESCROW-2222")
                .type(LedgerTransactionType.ORDER_ESCROW_HOLD)
                .status(LedgerTransactionStatus.COMPLETED)
                .amount(200_000L)
                .description("Ký quỹ đơn hàng")
                .createdAt(Instant.now())
                .build();
        escrowTx.addEntry(LedgerEntry.builder()
                .accountCode("USER_WALLET_" + USER_ID)
                .credit(0L)
                .debit(200_000L)
                .build());

        Page<LedgerTransaction> mockPage = new PageImpl<>(List.of(topupTx, escrowTx));
        when(ledgerTransactionRepository.findTransactions(eq(USER_ID), eq("USER_WALLET_" + USER_ID), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(mockPage);

        WalletTransactionFilterRequest filter = new WalletTransactionFilterRequest();
        PageResponse<WalletTransactionResponse> result = walletService.getTransactionHistory(USER_ID, filter);

        assertNotNull(result);
        assertEquals(2, result.content().size());
        assertEquals("IN", result.content().get(0).getBalanceDirection());
        assertEquals(500_000L, result.content().get(0).getAmount());
        assertEquals("OUT", result.content().get(1).getBalanceDirection());
        assertEquals(200_000L, result.content().get(1).getAmount());
    }

    @Test
    @DisplayName("getMonthlyStatement should calculate starting balance, inflow, outflow, and ending balance accurately (UC-38, API-090)")
    void testGetMonthlyStatement_Success() {
        when(walletRepository.findByUserId(USER_ID)).thenReturn(Optional.of(Wallet.builder().userId(USER_ID).available(600_000L).build()));
        when(ledgerEntryRepository.calculateBalanceBefore(eq("USER_WALLET_" + USER_ID), any(Instant.class)))
                .thenReturn(500_000L);

        LedgerTransaction topupTx = LedgerTransaction.builder()
                .id(1L)
                .transactionCode("LTX-TOPUP-01")
                .type(LedgerTransactionType.WALLET_TOPUP)
                .status(LedgerTransactionStatus.COMPLETED)
                .amount(300_000L)
                .description("Nạp tiền")
                .createdAt(Instant.now())
                .build();
        topupTx.addEntry(LedgerEntry.builder()
                .accountCode("USER_WALLET_" + USER_ID)
                .credit(300_000L)
                .debit(0L)
                .build());

        LedgerTransaction escrowTx = LedgerTransaction.builder()
                .id(2L)
                .transactionCode("LTX-ESCROW-02")
                .type(LedgerTransactionType.ORDER_ESCROW_HOLD)
                .status(LedgerTransactionStatus.COMPLETED)
                .amount(200_000L)
                .description("Ký quỹ")
                .createdAt(Instant.now())
                .build();
        escrowTx.addEntry(LedgerEntry.builder()
                .accountCode("USER_WALLET_" + USER_ID)
                .credit(0L)
                .debit(200_000L)
                .build());

        when(ledgerTransactionRepository.findTransactionsInRange(eq(USER_ID), eq("USER_WALLET_" + USER_ID), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(topupTx, escrowTx));

        WalletStatementResponse statement = walletService.getMonthlyStatement(USER_ID, 10, 2026);

        assertNotNull(statement);
        assertEquals("10/2026", statement.getPeriod());
        assertEquals(500_000L, statement.getStartingBalance());
        assertEquals(300_000L, statement.getTotalInflow());
        assertEquals(200_000L, statement.getTotalOutflow());
        assertEquals(100_000L, statement.getNetChange());
        assertEquals(600_000L, statement.getEndingBalance());
        assertEquals(2, statement.getTransactionCount());
    }

    @Test
    @DisplayName("getMonthlyStatement should throw exception when month or year is invalid")
    void testGetMonthlyStatement_InvalidPeriod() {
        AppException exMonth = assertThrows(AppException.class, () ->
                walletService.getMonthlyStatement(USER_ID, 13, 2026));
        assertEquals(PaymentErrorCode.WALLET_STATEMENT_PERIOD_INVALID, exMonth.getErrorCode());

        AppException exYear = assertThrows(AppException.class, () ->
                walletService.getMonthlyStatement(USER_ID, 5, 2018));
        assertEquals(PaymentErrorCode.WALLET_STATEMENT_PERIOD_INVALID, exYear.getErrorCode());
    }
}

