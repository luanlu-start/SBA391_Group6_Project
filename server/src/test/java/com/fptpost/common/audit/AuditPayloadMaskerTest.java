package com.fptpost.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class AuditPayloadMaskerTest {
    private final AuditPayloadMasker masker = new AuditPayloadMasker(new ObjectMapper());

    @Test
    void masksNestedSecretsAndIdentityValuesInMapsListsAndArraysWithoutMutatingInput() throws Exception {
        Map<String, Object> child = new LinkedHashMap<>(Map.of(
                "Password_Hash", "private-password-hash", "OTP_hash", "private-otp-hash",
                "access-token-hash", "private-token-hash", "CCCD_front", "private-cccd-image",
                "national_id_number", "private-identity", "price", 12000));
        Map<String, Object> source = Map.of("nested", List.of(child),
                "array", new Object[]{Map.of("otpCode", "private-otp-code")}, "parameterKey", "BASE_FARE");
        Map<String, Object> masked = masker.mask(source);
        String json = new ObjectMapper().writeValueAsString(masked);
        assertThat(json).doesNotContain("private-password-hash", "private-otp-hash", "private-token-hash",
                "private-cccd-image", "private-identity", "private-otp-code");
        assertThat(json).contains("[REDACTED]", "12000", "BASE_FARE");
        assertThat(child).containsEntry("OTP_hash", "private-otp-hash");
        assertThatThrownBy(() -> masked.put("tamper", "value")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void excessiveNestingIsRedactedRatherThanExposingUninspectedPayload() throws Exception {
        Object nested = Map.of("password", "private-deep-password");
        for (int depth = 0; depth < 25; depth++) nested = Map.of("child", nested);
        String masked = new ObjectMapper().writeValueAsString(masker.mask(Map.of("nested", nested)));
        assertThat(masked).contains("[REDACTED]").doesNotContain("private-deep-password");
    }

    @Test
    void reMaskingLegacyDataIsIdempotentAndNullPayloadHasAnEmptyObject() {
        var masked = masker.mask(Map.of("secret", "private-secret", "value", "10"));
        assertThat(masker.mask(masked)).isEqualTo(masked);
        assertThat(masker.mask(null)).isEmpty();
    }
}
