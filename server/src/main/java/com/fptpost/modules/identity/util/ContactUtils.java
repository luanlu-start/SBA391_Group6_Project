package com.fptpost.modules.identity.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class ContactUtils {
    private ContactUtils() {
    }

    public static String normalizePhone(String phone) {
        return phone.startsWith("0") ? "+84" + phone.substring(1) : phone;
    }

    public static String phoneHash(String normalizedPhone) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(normalizedPhone.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
