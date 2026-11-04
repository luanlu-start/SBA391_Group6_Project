package com.fptpost.modules.identity.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptpost.common.exception.AppException;
import com.fptpost.modules.identity.exception.IdentityErrorCode;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class KycCrypto {
    private final Map<String, byte[]> keys;
    private final String activeKeyId;
    private final byte[] hashKey;
    private final SecureRandom random;
    public KycCrypto(ObjectMapper mapper, SecureRandom identityRandom,
            @Value("${KYC_ENCRYPTION_KEYS:}") String keyring,
            @Value("${KYC_ACTIVE_KEY_ID:}") String activeKeyId,
            @Value("${KYC_HASH_KEY:}") String hashKey) {
        this.random = identityRandom; this.activeKeyId = activeKeyId;
        try {
            Map<String, String> configured = keyring.isBlank() ? Map.of() : mapper.readValue(keyring, new TypeReference<>() {});
            Map<String, byte[]> decoded = new HashMap<>();
            for (var entry : configured.entrySet()) {
                if (!entry.getKey().matches("[A-Za-z0-9_-]{1,40}")) throw new IllegalArgumentException();
                byte[] key = Base64.getDecoder().decode(entry.getValue());
                if (key.length != 32) throw new IllegalArgumentException(); decoded.put(entry.getKey(), key);
            }
            this.keys = Map.copyOf(decoded);
            this.hashKey = hashKey.isBlank() ? new byte[0] : Base64.getDecoder().decode(hashKey);
            if (this.hashKey.length != 0 && this.hashKey.length != 32) throw new IllegalArgumentException();
            if (decoded.values().stream().anyMatch(key -> MessageDigest.isEqual(key, this.hashKey))) throw new IllegalArgumentException();
        } catch (Exception exception) { throw new IllegalStateException("Invalid KYC key configuration; expected a keyring and separate 256-bit hash key"); }
    }
    public byte[] encrypt(byte[] plaintext, String context) {
        byte[] key = keys.get(activeKeyId);
        if (key == null || hashKey.length != 32) throw unavailable();
        try {
            byte[] nonce = new byte[12]; random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
            byte[] id = activeKeyId.getBytes(StandardCharsets.US_ASCII), ciphertext = cipher.doFinal(plaintext);
            return ByteBuffer.allocate(2 + id.length + nonce.length + ciphertext.length).put((byte)1).put((byte)id.length).put(id).put(nonce).put(ciphertext).array();
        } catch (GeneralSecurityException exception) { throw unavailable(); }
    }
    public byte[] decrypt(byte[] payload, String context) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(payload);
            if (buffer.get() != 1) throw new IllegalArgumentException();
            int length = Byte.toUnsignedInt(buffer.get()); if (length < 1 || length > 40 || buffer.remaining() < length + 12 + 16) throw new IllegalArgumentException();
            byte[] id = new byte[length]; buffer.get(id); byte[] key = keys.get(new String(id, StandardCharsets.US_ASCII));
            if (key == null) throw new IllegalArgumentException();
            byte[] nonce = new byte[12]; buffer.get(nonce); byte[] encrypted = new byte[buffer.remaining()]; buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));
            cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8)); return cipher.doFinal(encrypted);
        } catch (GeneralSecurityException | RuntimeException exception) { throw unavailable(); }
    }
    public String identityHash(String normalizedId) { return mac("id-number:v1:" + normalizedId); }
    public String sign(String value) { return mac("kyc-file-link:v1:" + value); }
    public boolean verify(String value, String signature) {
        return signature != null && signature.matches("[a-f0-9]{64}")
                && MessageDigest.isEqual(sign(value).getBytes(StandardCharsets.US_ASCII), signature.getBytes(StandardCharsets.US_ASCII));
    }
    private String mac(String value) {
        if (hashKey.length != 32) throw unavailable();
        try { Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(hashKey,"HmacSHA256")); return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8))); }
        catch (GeneralSecurityException exception) { throw unavailable(); }
    }
    private AppException unavailable() { return new AppException(IdentityErrorCode.KYC_ENCRYPTION_UNAVAILABLE); }
}
