package com.fptpost.modules.payment.util;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * Production-grade utility class for VNPay cryptography, hashing, and parameter encoding.
 */
@Slf4j
public final class VNPayUtil {

    private static final String HMAC_SHA512_ALGORITHM = "HmacSHA512";

    private VNPayUtil() {
        // Utility class
    }

    /**
     * Computes HMAC-SHA512 hex digest of the given data using the secret key.
     *
     * @param key  VNPay secret hash key (vnp_HashSecret)
     * @param data Raw query/hash string
     * @return 128-character lowercase hexadecimal hash string
     */
    public static String hmacSHA512(final String key, final String data) {
        if (key == null || data == null) {
            throw new IllegalArgumentException("Key and data must not be null for HMAC-SHA512 calculation");
        }
        try {
            Mac hmac512 = Mac.getInstance(HMAC_SHA512_ALGORITHM);
            SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), HMAC_SHA512_ALGORITHM);
            hmac512.init(secretKeySpec);
            byte[] hashBytes = hmac512.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(2 * hashBytes.length);
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception ex) {
            log.error("Failed to calculate HMAC-SHA512 hash: {}", ex.getMessage(), ex);
            throw new IllegalStateException("Failed to calculate HMAC-SHA512 hash", ex);
        }
    }

    /**
     * Builds both the hashData string and the queryString strictly adhering to VNPay's rules:
     * 1. Exclude null or empty values.
     * 2. Exclude 'vnp_SecureHash' and 'vnp_SecureHashType'.
     * 3. Sort keys alphabetically.
     * 4. URL-encode parameters.
     *
     * @param params Map of VNPay parameters
     * @return HashDataAndQuery holding hashData (for HMAC signing) and queryString (for URL)
     */
    public static HashDataAndQuery buildHashDataAndQuery(Map<String, String> params) {
        List<String> fieldNames = new ArrayList<>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key != null && !"vnp_SecureHash".equals(key) && !"vnp_SecureHashType".equals(key)
                    && value != null && !value.trim().isEmpty()) {
                fieldNames.add(key);
            }
        }

        Collections.sort(fieldNames);

        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();

        for (int i = 0; i < fieldNames.size(); i++) {
            String fieldName = fieldNames.get(i);
            String fieldValue = params.get(fieldName);

            try {
                String encodedFieldName = URLEncoder.encode(fieldName, StandardCharsets.US_ASCII.name());
                String encodedFieldValue = URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.name());

                hashData.append(fieldName).append('=').append(encodedFieldValue);
                query.append(encodedFieldName).append('=').append(encodedFieldValue);

                if (i < fieldNames.size() - 1) {
                    hashData.append('&');
                    query.append('&');
                }
            } catch (Exception e) {
                log.error("Failed to URL encode parameter {}: {}", fieldName, e.getMessage());
            }
        }

        return new HashDataAndQuery(hashData.toString(), query.toString());
    }

    /**
     * Validates incoming callback or IPN request parameters against the received vnp_SecureHash.
     *
     * @param params           Request parameter map
     * @param secretKey        VNPay secret key
     * @param targetSecureHash Received vnp_SecureHash
     * @return true if checksum matches, false otherwise
     */
    public static boolean verifySignature(Map<String, String> params, String secretKey, String targetSecureHash) {
        if (targetSecureHash == null || targetSecureHash.trim().isEmpty() || secretKey == null) {
            return false;
        }

        List<String> fieldNames = new ArrayList<>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key != null && !"vnp_SecureHash".equals(key) && !"vnp_SecureHashType".equals(key)
                    && value != null && !value.trim().isEmpty()) {
                fieldNames.add(key);
            }
        }

        Collections.sort(fieldNames);

        StringBuilder hashData = new StringBuilder();
        for (int i = 0; i < fieldNames.size(); i++) {
            String fieldName = fieldNames.get(i);
            String fieldValue = params.get(fieldName);

            try {
                String encodedFieldValue = URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.name());
                hashData.append(fieldName).append('=').append(encodedFieldValue);

                if (i < fieldNames.size() - 1) {
                    hashData.append('&');
                }
            } catch (Exception e) {
                log.error("Error encoding field {} during signature verification: {}", fieldName, e.getMessage());
            }
        }

        String calculatedHash = hmacSHA512(secretKey, hashData.toString());

        // Constant-time comparison to prevent timing attacks
        return MessageDigest.isEqual(
                calculatedHash.toLowerCase().getBytes(StandardCharsets.UTF_8),
                targetSecureHash.trim().toLowerCase().getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * Robust client IP address resolver supporting reverse proxies and Cloudflare/load-balancers.
     *
     * @param request HttpServletRequest
     * @return resolved client IP address
     */
    public static String getClientIpAddress(HttpServletRequest request) {
        if (request == null) {
            return "127.0.0.1";
        }

        String[] headers = {
                "X-Forwarded-For",
                "Proxy-Client-IP",
                "WL-Proxy-Client-IP",
                "HTTP_X_FORWARDED_FOR",
                "HTTP_X_FORWARDED",
                "HTTP_X_CLUSTER_CLIENT_IP",
                "HTTP_CLIENT_IP",
                "HTTP_FORWARDED_FOR",
                "HTTP_FORWARDED",
                "X-Real-IP"
        };

        for (String header : headers) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                // X-Forwarded-For can contain multiple IPs: "client, proxy1, proxy2"
                if (ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }
                return sanitizeIp(ip);
            }
        }

        return sanitizeIp(request.getRemoteAddr());
    }

    private static String sanitizeIp(String ip) {
        if (ip == null || ip.isEmpty() || "0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) {
            return "127.0.0.1";
        }
        return ip.trim();
    }

    /**
     * Value object holding hashData and queryString.
     */
    public record HashDataAndQuery(String hashData, String queryString) {}
}
