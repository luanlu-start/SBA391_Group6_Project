package com.fptpost.modules.payment.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("VNPayUtil Unit Tests")
class VNPayUtilTest {

    private static final String SECRET_KEY = "HDMKJXGPKGDZUQJDDBEFKXEIXZYWSNCM";

    @Test
    @DisplayName("Should generate accurate HMAC-SHA512 hash")
    void testHmacSHA512() {
        String testData = "vnp_Amount=10000000&vnp_Command=pay&vnp_CurrCode=VND&vnp_TmnCode=B91MXQ5Y";
        String hash = VNPayUtil.hmacSHA512(SECRET_KEY, testData);

        assertNotNull(hash);
        assertEquals(128, hash.length(), "HMAC-SHA512 hex digest should be 128 characters");
        assertEquals(hash.toLowerCase(), hash, "Hash should be in lowercase hex format");
    }

    @Test
    @DisplayName("Should sort keys alphabetically and format query string")
    void testBuildHashDataAndQuery() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_Command", "pay");
        params.put("vnp_Amount", "5000000");
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_TmnCode", "B91MXQ5Y");
        params.put("vnp_OrderInfo", "Thanh toan don hang test");

        VNPayUtil.HashDataAndQuery result = VNPayUtil.buildHashDataAndQuery(params);

        assertNotNull(result);
        assertNotNull(result.hashData());
        assertNotNull(result.queryString());

        // Verify alphabetical order: vnp_Amount comes before vnp_Command
        int amountIndex = result.hashData().indexOf("vnp_Amount");
        int commandIndex = result.hashData().indexOf("vnp_Command");
        int orderInfoIndex = result.hashData().indexOf("vnp_OrderInfo");
        int tmnCodeIndex = result.hashData().indexOf("vnp_TmnCode");
        int versionIndex = result.hashData().indexOf("vnp_Version");

        assertTrue(amountIndex < commandIndex);
        assertTrue(commandIndex < orderInfoIndex);
        assertTrue(orderInfoIndex < tmnCodeIndex);
        assertTrue(tmnCodeIndex < versionIndex);

        // Verify spaces encoded as '+' or '%20'
        assertTrue(result.hashData().contains("Thanh+toan+don+hang+test") || result.hashData().contains("Thanh%20toan%20don%20hang%20test"));
    }

    @Test
    @DisplayName("Should verify signature correctly when authentic and reject when altered")
    void testVerifySignature() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_Amount", "25000000");
        params.put("vnp_BankCode", "NCB");
        params.put("vnp_Command", "pay");
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_OrderInfo", "Thanh toan don hang ORD123");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TmnCode", "B91MXQ5Y");
        params.put("vnp_TransactionNo", "14123456");
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_TxnRef", "ORD123");

        VNPayUtil.HashDataAndQuery result = VNPayUtil.buildHashDataAndQuery(params);
        String correctHash = VNPayUtil.hmacSHA512(SECRET_KEY, result.hashData());

        // When receiving in callback, params will have raw values
        boolean valid = VNPayUtil.verifySignature(params, SECRET_KEY, correctHash);
        assertTrue(valid, "Signature should be valid when parameters match");

        // When tampered
        params.put("vnp_Amount", "10000000");
        boolean invalid = VNPayUtil.verifySignature(params, SECRET_KEY, correctHash);
        assertFalse(invalid, "Signature should fail when parameter amount was altered");
    }

    @Test
    @DisplayName("Should correctly resolve client IP address from proxy headers")
    void testGetClientIpAddress() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.113.152.1, 10.0.0.1");

        String ip = VNPayUtil.getClientIpAddress(request);
        assertEquals("203.113.152.1", ip);

        MockHttpServletRequest loopbackRequest = new MockHttpServletRequest();
        loopbackRequest.setRemoteAddr("0:0:0:0:0:0:0:1");
        assertEquals("127.0.0.1", VNPayUtil.getClientIpAddress(loopbackRequest));
    }
}
