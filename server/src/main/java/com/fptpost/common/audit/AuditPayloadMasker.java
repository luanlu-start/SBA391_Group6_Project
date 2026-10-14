package com.fptpost.common.audit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Masks at capture time, so secrets are absent from both SQL and MongoDB. */
@Component
public class AuditPayloadMasker {
    private static final Set<String> SENSITIVE_KEYS = Set.of("password", "passwordhash", "secret",
            "jwtsecret", "token", "accesstoken", "refreshtoken", "otp", "otpcode", "pin", "cccd",
            "cccdnumber", "nationalid", "nationalidnumber", "identitynumber", "idcardnumber",
            "signedurl", "authorization", "cookie", "email", "phone", "phonenumber",
            "emailaddress", "contactemail", "contactphone", "fullname", "address", "dateofbirth",
            "bankaccount", "bankaccountnumber", "apikey", "privatekey", "credential", "idnumber");
    private final ObjectMapper mapper;

    public AuditPayloadMasker(ObjectMapper mapper) { this.mapper = mapper; }

    public Map<String, Object> mask(Map<String, Object> source) {
        if (source == null) return Map.of();
        Map<String, Object> plain = mapper.convertValue(source, new TypeReference<>() { });
        return maskMap(plain, 0);
    }

    private Map<String, Object> maskMap(Map<?, ?> source, int depth) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            String name = String.valueOf(key);
            String normalized = name.replaceAll("[^a-zA-Z0-9]", "").toLowerCase(java.util.Locale.ROOT);
            boolean sensitive = SENSITIVE_KEYS.contains(normalized) || normalized.contains("password")
                    || normalized.contains("secret") || normalized.endsWith("token")
                    || normalized.startsWith("cccd") || normalized.startsWith("nationalid")
                    || normalized.startsWith("idcard");
            result.put(name, sensitive ? "[REDACTED]" : maskValue(value, depth + 1));
        });
        return Collections.unmodifiableMap(result);
    }

    private Object maskValue(Object value, int depth) {
        if (depth > 20) return "[REDACTED]";
        if (value instanceof Map<?, ?> map) return maskMap(map, depth);
        if (value instanceof List<?> list) return list.stream().map(item -> maskValue(item, depth + 1)).toList();
        return value;
    }
}
