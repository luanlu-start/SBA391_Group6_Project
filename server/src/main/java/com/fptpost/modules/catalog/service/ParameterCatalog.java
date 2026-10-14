package com.fptpost.modules.catalog.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptpost.common.exception.AppException;
import com.fptpost.modules.catalog.dto.internal.ParameterDefinition;
import com.fptpost.modules.catalog.enums.ParameterValueType;
import com.fptpost.modules.catalog.exception.ParameterErrorCode;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Immutable registry from the user-confirmed workbook 06_Tham_số, PRM-001 through PRM-109. */
@Component
public class ParameterCatalog {
    private static final Set<String> RATIOS = Set.of("FEE_NEW", "FEE_BRONZE", "FEE_SILVER", "FEE_GOLD",
            "FEE_DIAMOND", "INS_RATE", "FUND_SHARE", "CANCEL_RATE", "RETURN_FEE", "WITHHOLD",
            "W_ONTIME", "W_RATING", "W_COMPLETE", "W_COMPLAINT");
    private static final Set<String> SCORES = Set.of("TRUST_INIT", "TRUST_RESTRICT", "TRUST_REVIEW", "CANCEL_PENALTY");
    private static final Set<String> ALLOW_ZERO = Set.of("FREE_WD", "NOTIF_RETRY", "RECONCILE_AT");
    private static final Pattern NUMBER = Pattern.compile("[0-9]+(?:\\.[0-9]+)?");
    private static final Pattern CONTACT = Pattern.compile("([0-9]+)\\s+lần\\s+gọi\\s+trong\\s+([0-9]+)\\s+phút");
    private static final Pattern TIER = Pattern.compile("(?:≥|>=)\\s*([0-9]+(?:\\.[0-9]+)?)\\s+điểm\\s*&\\s*(?:≥|>=)\\s*([0-9]+)\\s+đơn");
    private static final Pattern CONC = Pattern.compile("Tân\\s+binh\\s+([0-9]+)\\s*/\\s*Đồng\\s+([0-9]+)\\s*/\\s*Bạc\\s+([0-9]+)\\s*/\\s*Vàng\\s+([0-9]+)\\s*/\\s*Kim\\s+cương\\s+([0-9]+)");
    private static final Pattern VALUE = Pattern.compile("Tân\\s+binh\\s+([0-9]+(?:\\.[0-9]+)?)\\s*/\\s*Đồng\\s+([0-9]+(?:\\.[0-9]+)?)\\s*/\\s*Bạc\\s+([0-9]+(?:\\.[0-9]+)?)\\s*/\\s*Vàng\\s+([0-9]+(?:\\.[0-9]+)?)\\s*/\\s*Kim\\s+cương\\s+([0-9]+(?:\\.[0-9]+)?)\\s*\\(triệu\\s+đồng\\)");
    private final Map<String, ParameterDefinition> definitions;

    public ParameterCatalog(ObjectMapper objectMapper) {
        try (var input = new ClassPathResource("catalog/parameters.json").getInputStream()) {
            List<ParameterDefinition> rows = objectMapper.readValue(input, new TypeReference<>() { });
            Map<String, ParameterDefinition> entries = new LinkedHashMap<>();
            Set<String> ids = new HashSet<>();
            for (ParameterDefinition definition : rows) {
                if (entries.putIfAbsent(definition.key(), definition) != null || !ids.add(definition.prmId())) {
                    throw new IllegalStateException("Duplicate key or PRM ID in required parameter catalog");
                }
            }
            if (entries.size() != 109) throw new IllegalStateException("Required catalog must contain 109 parameters");
            for (int index = 1; index <= 109; index++) {
                if (!ids.contains("PRM-%03d".formatted(index))) throw new IllegalStateException("Missing PRM identifier");
            }
            definitions = Collections.unmodifiableMap(entries);
            validateSnapshot(defaultValues());
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read required parameter catalog", exception);
        }
    }

    public Collection<ParameterDefinition> definitions() {
        return definitions.values();
    }

    public ParameterDefinition definition(String key) {
        ParameterDefinition definition = definitions.get(key);
        if (definition == null) throw new AppException(ParameterErrorCode.PARAMETER_NOT_FOUND);
        return definition;
    }

    public Map<String, String> defaultValues() {
        Map<String, String> values = new LinkedHashMap<>();
        definitions.values().forEach(row -> values.put(row.key(), row.defaultValue()));
        return Collections.unmodifiableMap(values);
    }

    public String normalize(String key, String suppliedValue) {
        ParameterDefinition definition = definition(key);
        if (suppliedValue == null || suppliedValue.isBlank() || suppliedValue.length() > 1000) invalidValue();
        String value = suppliedValue.strip();
        if (definition.valueType() == ParameterValueType.TEXT) return normalizeCompound(key, value);
        if (!NUMBER.matcher(value).matches()) invalidValue();
        BigDecimal numeric = decimal(value);
        if (definition.valueType() == ParameterValueType.INTEGER) {
            try {
                numeric.longValueExact();
            } catch (ArithmeticException exception) {
                invalidValue();
            }
        }
        if (numeric.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0) invalidValue();
        if (RATIOS.contains(key)) {
            requireValue(numeric.compareTo(BigDecimal.ONE) <= 0);
        } else if (SCORES.contains(key)) {
            requireValue(numeric.compareTo(BigDecimal.valueOf(100)) <= 0);
        } else if ("RECONCILE_AT".equals(key)) {
            requireValue(numeric.compareTo(BigDecimal.valueOf(23)) <= 0);
        } else if ("URGENT_MULT".equals(key) || "SAMEDAY_MULT".equals(key)) {
            requireValue(numeric.compareTo(BigDecimal.ONE) >= 0);
        } else if (!(ALLOW_ZERO.contains(key) || isMonetary(definition))) {
            requireValue(numeric.signum() > 0);
        }
        if ("ROUND_FEE".equals(key)) requireValue(numeric.signum() > 0);
        return numeric.stripTrailingZeros().toPlainString();
    }

    public void validateSnapshot(Map<String, String> values) {
        if (!values.keySet().equals(definitions.keySet())) constraintViolation();
        values.forEach(this::normalize);
        ordered(values, "SIZE_S", "SIZE_M", "MAX_SIDE");
        requireConstraint(number(values, "SIZE_S").compareTo(number(values, "SIZE_M")) < 0);
        ordered(values, "HIGH_VALUE", "MAX_DECLARED");
        ordered(values, "BASE_KM", "LONG_KM");
        requireConstraint(number(values, "BASE_KM").compareTo(number(values, "LONG_KM")) < 0);
        ordered(values, "BID_MIN", "BID_MAX");
        ordered(values, "SAMEDAY_MULT", "URGENT_MULT");
        ordered(values, "MIN_TOPUP", "MAX_TOPUP", "MAX_BALANCE");
        ordered(values, "MIN_WITHDRAW", "MAX_WITHDRAW", "MAX_WD_DAY");
        ordered(values, "MAX_WITHDRAW", "MAX_BALANCE");
        ordered(values, "TRUST_REVIEW", "TRUST_RESTRICT");
        requireConstraint(number(values, "OPEN_DEFAULT").compareTo(number(values, "OPEN_MAX")
                .multiply(BigDecimal.valueOf(24))) <= 0);
        BigDecimal weight = number(values, "W_ONTIME").add(number(values, "W_RATING"))
                .add(number(values, "W_COMPLETE")).add(number(values, "W_COMPLAINT"));
        requireConstraint(weight.compareTo(BigDecimal.ONE) == 0);
        for (String feeKey : List.of("FEE_NEW", "FEE_BRONZE", "FEE_SILVER", "FEE_GOLD", "FEE_DIAMOND")) {
            requireConstraint(number(values, feeKey).add(number(values, "WITHHOLD")).compareTo(BigDecimal.ONE) <= 0);
        }
        List<BigDecimal> previous = null;
        for (String key : List.of("TIER_BRONZE", "TIER_SILVER", "TIER_GOLD", "TIER_DIAMOND")) {
            List<BigDecimal> current = groups(TIER, values.get(key));
            if (previous != null) {
                requireConstraint(previous.get(0).compareTo(current.get(0)) <= 0
                        && previous.get(1).compareTo(current.get(1)) <= 0);
            }
            previous = current;
        }
        requireConstraint(number(values, "TRUST_RESTRICT").compareTo(groups(TIER, values.get("TIER_BRONZE")).get(0)) <= 0);
    }

    private String normalizeCompound(String key, String value) {
        return switch (key) {
            case "CONTACT_TRIES" -> {
                var numbers = groups(CONTACT, value);
                numbers.forEach(number -> requireValue(number.signum() > 0 && fitsLong(number)));
                yield numbers.get(0).toPlainString() + " lần gọi trong " + numbers.get(1).toPlainString() + " phút";
            }
            case "TIER_BRONZE", "TIER_SILVER", "TIER_GOLD", "TIER_DIAMOND" -> {
                var numbers = groups(TIER, value);
                requireValue(numbers.get(0).compareTo(BigDecimal.valueOf(100)) <= 0 && fitsLong(numbers.get(1)));
                yield "≥ " + numbers.get(0).stripTrailingZeros().toPlainString() + " điểm & ≥ "
                        + numbers.get(1).toPlainString() + " đơn";
            }
            case "CONC_TIER", "VALUE_TIER" -> {
                var numbers = groups("CONC_TIER".equals(key) ? CONC : VALUE, value);
                for (int index = 0; index < numbers.size(); index++) {
                    requireValue(numbers.get(index).signum() > 0
                            && numbers.get(index).compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) <= 0);
                    if (index > 0) requireConstraint(numbers.get(index - 1).compareTo(numbers.get(index)) <= 0);
                }
                yield "Tân binh " + plain(numbers.get(0)) + " / Đồng " + plain(numbers.get(1))
                        + " / Bạc " + plain(numbers.get(2)) + " / Vàng " + plain(numbers.get(3))
                        + " / Kim cương " + plain(numbers.get(4))
                        + ("VALUE_TIER".equals(key) ? " (triệu đồng)" : "");
            }
            default -> throw new IllegalStateException("Compound registry lacks a validator");
        };
    }

    private List<BigDecimal> groups(Pattern pattern, String value) {
        Matcher matcher = pattern.matcher(value);
        if (!matcher.matches()) invalidValue();
        List<BigDecimal> numbers = new ArrayList<>();
        for (int index = 1; index <= matcher.groupCount(); index++) numbers.add(decimal(matcher.group(index)));
        return numbers;
    }

    private static boolean fitsLong(BigDecimal value) {
        try { value.longValueExact(); return true; } catch (ArithmeticException exception) { return false; }
    }

    private static String plain(BigDecimal value) { return value.stripTrailingZeros().toPlainString(); }
    private static boolean isMonetary(ParameterDefinition definition) {
        return definition.unit() != null && definition.unit().startsWith("VND");
    }
    private static BigDecimal number(Map<String, String> values, String key) { return decimal(values.get(key)); }
    private static BigDecimal decimal(String value) {
        try { return new BigDecimal(value); }
        catch (NumberFormatException exception) { throw new AppException(ParameterErrorCode.PARAMETER_INVALID_VALUE); }
    }
    private static void ordered(Map<String, String> values, String... keys) {
        for (int index = 1; index < keys.length; index++) {
            requireConstraint(number(values, keys[index - 1]).compareTo(number(values, keys[index])) <= 0);
        }
    }
    private static void requireValue(boolean valid) { if (!valid) invalidValue(); }
    private static void requireConstraint(boolean valid) { if (!valid) constraintViolation(); }
    private static void invalidValue() { throw new AppException(ParameterErrorCode.PARAMETER_INVALID_VALUE); }
    private static void constraintViolation() { throw new AppException(ParameterErrorCode.PARAMETER_CONSTRAINT_VIOLATION); }
}
