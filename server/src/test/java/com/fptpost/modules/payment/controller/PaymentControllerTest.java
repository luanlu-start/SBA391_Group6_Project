package com.fptpost.modules.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptpost.modules.payment.dto.PaymentCreationRequest;
import com.fptpost.modules.payment.dto.PaymentCreationResponse;
import com.fptpost.modules.payment.dto.VNPayIpnResponse;
import com.fptpost.modules.payment.dto.VNPayReturnResponse;
import com.fptpost.modules.payment.service.VNPayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentController Web Layer Tests")
class PaymentControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private VNPayService vnPayService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PaymentController(vnPayService)).build();
    }

    @Test
    @DisplayName("POST /api/payment/create-vnpay-url should return 200 with redirect URL")
    void testCreatePaymentUrl() throws Exception {
        PaymentCreationRequest request = PaymentCreationRequest.builder()
                .orderCode("ORD-20261013-001")
                .amount(BigDecimal.valueOf(250000))
                .orderInfo("Thanh toan don hang ORD-20261013-001")
                .bankCode("NCB")
                .language("vn")
                .build();

        PaymentCreationResponse response = PaymentCreationResponse.builder()
                .code("00")
                .message("success")
                .paymentUrl("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_Amount=25000000")
                .txnRef("ORD-20261013-001")
                .build();

        when(vnPayService.createPaymentUrl(any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/payment/create-vnpay-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value("00"))
                .andExpect(jsonPath("$.data.paymentUrl").value("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_Amount=25000000"))
                .andExpect(jsonPath("$.data.txnRef").value("ORD-20261013-001"));
    }

    @Test
    @DisplayName("GET /api/payment/vnpay-return should return 200 with parsed status")
    void testHandleVNPayReturn() throws Exception {
        VNPayReturnResponse response = VNPayReturnResponse.builder()
                .orderCode("ORD-20261013-001")
                .amount(250000L)
                .bankCode("NCB")
                .responseCode("00")
                .transactionStatus("00")
                .validSignature(true)
                .success(true)
                .message("Giao dịch thành công")
                .build();

        when(vnPayService.processReturn(any(Map.class))).thenReturn(response);

        mockMvc.perform(get("/api/payment/vnpay-return")
                        .param("vnp_TxnRef", "ORD-20261013-001")
                        .param("vnp_Amount", "25000000")
                        .param("vnp_ResponseCode", "00")
                        .param("vnp_TransactionStatus", "00")
                        .param("vnp_SecureHash", "mock_hash"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderCode").value("ORD-20261013-001"))
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.validSignature").value(true));
    }

    @Test
    @DisplayName("GET /api/payment/vnpay-ipn should return 200 with VNPay standard RspCode and Message")
    void testHandleVNPayIpn() throws Exception {
        when(vnPayService.processIpn(any(Map.class))).thenReturn(VNPayIpnResponse.success());

        mockMvc.perform(get("/api/payment/vnpay-ipn")
                        .param("vnp_TxnRef", "ORD-20261013-001")
                        .param("vnp_Amount", "25000000")
                        .param("vnp_ResponseCode", "00")
                        .param("vnp_TransactionStatus", "00")
                        .param("vnp_SecureHash", "mock_hash"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("00"))
                .andExpect(jsonPath("$.Message").value("Confirm Success"));
    }
}
