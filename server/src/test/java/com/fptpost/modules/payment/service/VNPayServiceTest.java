package com.fptpost.modules.payment.service;

import com.fptpost.modules.order.repository.OrderRepository;
import com.fptpost.modules.payment.config.VNPayConfig;
import com.fptpost.modules.payment.dto.PaymentCreationRequest;
import com.fptpost.modules.payment.dto.PaymentCreationResponse;
import com.fptpost.modules.payment.dto.VNPayIpnResponse;
import com.fptpost.modules.payment.dto.VNPayReturnResponse;
import com.fptpost.modules.payment.model.PaymentStatus;
import com.fptpost.modules.payment.model.PaymentTransaction;
import com.fptpost.modules.payment.repository.PaymentTransactionRepository;
import com.fptpost.modules.payment.util.VNPayUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("VNPayService Unit Tests")
class VNPayServiceTest {

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private com.fptpost.modules.payment.repository.WalletRepository walletRepository;

    @Mock
    private com.fptpost.modules.payment.repository.EscrowRepository escrowRepository;

    @Mock
    private com.fptpost.modules.payment.service.LedgerService ledgerService;

    private VNPayConfig vnPayConfig;
    private VNPayService vnPayService;

    private static final String SECRET_KEY = "test-only-vnpay-hmac-key-not-for-real-payments";
    private static final String TMN_CODE = "B91MXQ5Y";

    @BeforeEach
    void setUp() {
        vnPayConfig = new VNPayConfig();
        vnPayConfig.setVnp_TmnCode(TMN_CODE);
        vnPayConfig.setVnp_HashSecret(SECRET_KEY);
        vnPayConfig.setVnp_PayUrl("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        vnPayConfig.setVnp_ReturnUrl("http://localhost:8080/api/payment/vnpay-return");
        vnPayConfig.setTimeoutMinutes(15);

        vnPayService = new VNPayService(vnPayConfig, paymentTransactionRepository, Optional.of(orderRepository), walletRepository, escrowRepository, ledgerService);
    }

    @Test
    @DisplayName("Should generate signed payment URL multiplying amount by 100")
    void testCreatePaymentUrl() {
        PaymentCreationRequest request = PaymentCreationRequest.builder()
                .orderCode("ORD-1001")
                .amount(BigDecimal.valueOf(150000)) // 150,000 VND
                .orderInfo("Thanh toan don hang ORD-1001")
                .bankCode("NCB")
                .language("vn")
                .build();

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.setRemoteAddr("127.0.0.1");

        when(paymentTransactionRepository.existsByTxnRef("ORD-1001")).thenReturn(false);
        when(paymentTransactionRepository.save(any(PaymentTransaction.class))).thenAnswer(i -> i.getArguments()[0]);

        PaymentCreationResponse response = vnPayService.createPaymentUrl(request, httpRequest);

        assertNotNull(response);
        assertEquals("00", response.getCode());
        assertEquals("success", response.getMessage());
        assertEquals("ORD-1001", response.getTxnRef());

        String paymentUrl = response.getPaymentUrl();
        assertTrue(paymentUrl.startsWith("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?"));
        assertTrue(paymentUrl.contains("vnp_Amount=15000000"), "Amount should be 150000 * 100");
        assertTrue(paymentUrl.contains("vnp_TmnCode=" + TMN_CODE));
        assertTrue(paymentUrl.contains("vnp_TxnRef=ORD-1001"));
        assertTrue(paymentUrl.contains("vnp_SecureHash="));

        verify(paymentTransactionRepository, times(1)).save(any(PaymentTransaction.class));
    }

    @Test
    @DisplayName("Should parse return callback successfully")
    void testProcessReturn() {
        Map<String, String> queryParams = new HashMap<>();
        queryParams.put("vnp_Amount", "15000000");
        queryParams.put("vnp_BankCode", "NCB");
        queryParams.put("vnp_BankTranNo", "VNP12345678");
        queryParams.put("vnp_CardType", "ATM");
        queryParams.put("vnp_OrderInfo", "Thanh toan don hang ORD-1001");
        queryParams.put("vnp_PayDate", "20261013180000");
        queryParams.put("vnp_ResponseCode", "00");
        queryParams.put("vnp_TmnCode", TMN_CODE);
        queryParams.put("vnp_TransactionNo", "14999999");
        queryParams.put("vnp_TransactionStatus", "00");
        queryParams.put("vnp_TxnRef", "ORD-1001");

        VNPayUtil.HashDataAndQuery hashData = VNPayUtil.buildHashDataAndQuery(queryParams);
        String secureHash = VNPayUtil.hmacSHA512(SECRET_KEY, hashData.hashData());
        queryParams.put("vnp_SecureHash", secureHash);

        VNPayReturnResponse returnResponse = vnPayService.processReturn(queryParams);

        assertNotNull(returnResponse);
        assertTrue(returnResponse.isValidSignature());
        assertTrue(returnResponse.isSuccess());
        assertEquals("ORD-1001", returnResponse.getOrderCode());
        assertEquals(150000L, returnResponse.getAmount());
        assertEquals("Giao dịch thành công", returnResponse.getMessage());
    }

    @Test
    @DisplayName("IPN: Should return 97 when signature is invalid")
    void testProcessIpn_InvalidChecksum() {
        Map<String, String> queryParams = new HashMap<>();
        queryParams.put("vnp_TxnRef", "ORD-1001");
        queryParams.put("vnp_Amount", "15000000");
        queryParams.put("vnp_SecureHash", "invalid_checksum_value");

        VNPayIpnResponse ipnResponse = vnPayService.processIpn(queryParams);

        assertEquals("97", ipnResponse.getRspCode());
        assertEquals("Invalid Checksum", ipnResponse.getMessage());
    }

    @Test
    @DisplayName("IPN: Should return 01 when order is not found")
    void testProcessIpn_OrderNotFound() {
        Map<String, String> queryParams = new HashMap<>();
        queryParams.put("vnp_Amount", "15000000");
        queryParams.put("vnp_TxnRef", "NON_EXISTENT_ORDER");
        queryParams.put("vnp_ResponseCode", "00");
        queryParams.put("vnp_TransactionStatus", "00");

        VNPayUtil.HashDataAndQuery hashData = VNPayUtil.buildHashDataAndQuery(queryParams);
        String secureHash = VNPayUtil.hmacSHA512(SECRET_KEY, hashData.hashData());
        queryParams.put("vnp_SecureHash", secureHash);

        when(paymentTransactionRepository.findByTxnRef("NON_EXISTENT_ORDER")).thenReturn(Optional.empty());
        when(paymentTransactionRepository.findFirstByOrderCodeOrderByCreatedAtDesc("NON_EXISTENT_ORDER")).thenReturn(Optional.empty());

        VNPayIpnResponse ipnResponse = vnPayService.processIpn(queryParams);

        assertEquals("01", ipnResponse.getRspCode());
        assertEquals("Order not Found", ipnResponse.getMessage());
    }

    @Test
    @DisplayName("IPN: Should return 04 when amount mismatch occurs")
    void testProcessIpn_InvalidAmount() {
        Map<String, String> queryParams = new HashMap<>();
        queryParams.put("vnp_Amount", "5000000"); // 50,000 VND received
        queryParams.put("vnp_TxnRef", "ORD-1001");
        queryParams.put("vnp_ResponseCode", "00");
        queryParams.put("vnp_TransactionStatus", "00");

        VNPayUtil.HashDataAndQuery hashData = VNPayUtil.buildHashDataAndQuery(queryParams);
        String secureHash = VNPayUtil.hmacSHA512(SECRET_KEY, hashData.hashData());
        queryParams.put("vnp_SecureHash", secureHash);

        PaymentTransaction transaction = PaymentTransaction.builder()
                .txnRef("ORD-1001")
                .orderCode("ORD-1001")
                .amount(BigDecimal.valueOf(150000)) // 150,000 VND expected
                .status(PaymentStatus.PENDING)
                .build();

        when(paymentTransactionRepository.findByTxnRef("ORD-1001")).thenReturn(Optional.of(transaction));

        VNPayIpnResponse ipnResponse = vnPayService.processIpn(queryParams);

        assertEquals("04", ipnResponse.getRspCode());
        assertEquals("Invalid Amount", ipnResponse.getMessage());
    }

    @Test
    @DisplayName("IPN: Should return 02 when order is already confirmed (Idempotency)")
    void testProcessIpn_AlreadyConfirmed() {
        Map<String, String> queryParams = new HashMap<>();
        queryParams.put("vnp_Amount", "15000000");
        queryParams.put("vnp_TxnRef", "ORD-1001");
        queryParams.put("vnp_ResponseCode", "00");
        queryParams.put("vnp_TransactionStatus", "00");

        VNPayUtil.HashDataAndQuery hashData = VNPayUtil.buildHashDataAndQuery(queryParams);
        String secureHash = VNPayUtil.hmacSHA512(SECRET_KEY, hashData.hashData());
        queryParams.put("vnp_SecureHash", secureHash);

        PaymentTransaction transaction = PaymentTransaction.builder()
                .txnRef("ORD-1001")
                .orderCode("ORD-1001")
                .amount(BigDecimal.valueOf(150000))
                .status(PaymentStatus.SUCCESS) // Already confirmed!
                .build();

        when(paymentTransactionRepository.findByTxnRef("ORD-1001")).thenReturn(Optional.of(transaction));

        VNPayIpnResponse ipnResponse = vnPayService.processIpn(queryParams);

        assertEquals("02", ipnResponse.getRspCode());
        assertEquals("Order already confirmed", ipnResponse.getMessage());
    }

    @Test
    @DisplayName("IPN: Should return 00 and confirm payment when valid")
    void testProcessIpn_Success() {
        Map<String, String> queryParams = new HashMap<>();
        queryParams.put("vnp_Amount", "15000000");
        queryParams.put("vnp_TxnRef", "ORD-1001");
        queryParams.put("vnp_ResponseCode", "00");
        queryParams.put("vnp_TransactionStatus", "00");
        queryParams.put("vnp_TransactionNo", "14555555");
        queryParams.put("vnp_BankCode", "NCB");

        VNPayUtil.HashDataAndQuery hashData = VNPayUtil.buildHashDataAndQuery(queryParams);
        String secureHash = VNPayUtil.hmacSHA512(SECRET_KEY, hashData.hashData());
        queryParams.put("vnp_SecureHash", secureHash);

        PaymentTransaction transaction = PaymentTransaction.builder()
                .txnRef("ORD-1001")
                .orderCode("ORD-1001")
                .amount(BigDecimal.valueOf(150000))
                .status(PaymentStatus.PENDING)
                .build();

        when(paymentTransactionRepository.findByTxnRef("ORD-1001")).thenReturn(Optional.of(transaction));
        when(paymentTransactionRepository.save(any(PaymentTransaction.class))).thenAnswer(i -> i.getArguments()[0]);

        VNPayIpnResponse ipnResponse = vnPayService.processIpn(queryParams);

        assertEquals("00", ipnResponse.getRspCode());
        assertEquals("Confirm Success", ipnResponse.getMessage());
        assertEquals(PaymentStatus.SUCCESS, transaction.getStatus());
        assertEquals("14555555", transaction.getTransactionNo());

        verify(paymentTransactionRepository, times(1)).save(transaction);
    }
}
