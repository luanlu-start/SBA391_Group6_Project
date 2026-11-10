package com.fptpost.modules.payment.service;

import com.fptpost.common.exception.AppException;
import com.fptpost.modules.identity.enums.KycStatus;
import com.fptpost.modules.identity.model.KycRequest;
import com.fptpost.modules.identity.model.UserProfile;
import com.fptpost.modules.identity.repository.KycRequestRepository;
import com.fptpost.modules.identity.repository.UserProfileRepository;
import com.fptpost.modules.identity.security.KycCrypto;
import com.fptpost.modules.payment.config.VNPayConfig;
import com.fptpost.modules.payment.dto.BankAccountCreationRequest;
import com.fptpost.modules.payment.dto.BankAccountResponse;
import com.fptpost.modules.payment.exception.PaymentErrorCode;
import com.fptpost.modules.payment.model.BankAccount;
import com.fptpost.modules.payment.repository.BankAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Service quản lý tài khoản ngân hàng nhận tiền (UC-39).
 * Đối chiếu tên chủ tài khoản với tên KYC (BR-R-058), mã hóa AES-256-GCM số tài khoản (BR-N-026),
 * và áp dụng thời gian chờ bảo mật 24 giờ trước khi rút tiền (PRM-066).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BankAccountService {

    public static final long DEFAULT_BANK_COOLDOWN_HOURS = 24L;
    private static final String FALLBACK_PREFIX = "BANKGCM:v1:";
    private static final String DEFAULT_SECRET_SEED = "FPTPOST_BANK_ACCOUNT_AES256_GCM_SECRET_KEY_2026";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final Map<String, String> BANK_CODE_NAMES = Map.ofEntries(
            Map.entry("VCB", "Vietcombank"),
            Map.entry("TCB", "Techcombank"),
            Map.entry("MB", "MB Bank"),
            Map.entry("ACB", "ACB"),
            Map.entry("BIDV", "BIDV"),
            Map.entry("CTG", "VietinBank"),
            Map.entry("VTB", "VietinBank"),
            Map.entry("VPB", "VPBank"),
            Map.entry("TPB", "TPBank"),
            Map.entry("STB", "Sacombank"),
            Map.entry("VIB", "VIB"),
            Map.entry("HDB", "HDBank"),
            Map.entry("SHB", "SHB"),
            Map.entry("OCB", "OCB"),
            Map.entry("MSB", "MSB"),
            Map.entry("AGRIBANK", "Agribank")
    );

    private final BankAccountRepository bankAccountRepository;
    private final KycRequestRepository kycRequestRepository;
    private final UserProfileRepository userProfileRepository;
    private final KycCrypto kycCrypto;
    private final VNPayConfig vnPayConfig;

    /**
     * Lấy danh sách tài khoản ngân hàng đã liên kết của người dùng (UC-39, API-094).
     */
    @Transactional(readOnly = true)
    public List<BankAccountResponse> getMyBankAccounts(Long userId) {
        return bankAccountRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    /**
     * Thêm tài khoản ngân hàng nhận tiền mới (UC-39, API-095).
     */
    @Transactional
    public BankAccountResponse createBankAccount(Long userId, BankAccountCreationRequest request) {
        // 1. Kiểm tra KYC đã duyệt và lấy họ tên xác minh (BR-R-013, BR-R-058)
        String kycFullName = resolveApprovedKycFullName(userId);
        String normalizedKycName = normalizeName(kycFullName);
        String normalizedHolderName = normalizeName(request.getHolderName());

        if (normalizedHolderName.isEmpty() || !normalizedHolderName.equals(normalizedKycName)) {
            log.warn("Bank account holder mismatch for userId={}: holder='{}' (norm='{}') vs kyc='{}' (norm='{}')",
                    userId, request.getHolderName(), normalizedHolderName, kycFullName, normalizedKycName);
            throw new AppException(PaymentErrorCode.BANK_ACCOUNT_HOLDER_MISMATCH);
        }

        // 2. Chuẩn hóa thông tin ngân hàng & số tài khoản
        String bankCode = request.getBankCode().trim().toUpperCase(Locale.ROOT);
        String bankName = resolveBankName(bankCode, request.getBankName());
        String accountNumber = request.getAccountNumber().trim().replaceAll("\\s+", "");
        if (!accountNumber.matches("^[0-9]{6,25}$")) {
            throw new AppException(PaymentErrorCode.BANK_ACCOUNT_HOLDER_MISMATCH);
        }

        String accountNoHash = hashAccountNumber(bankCode, accountNumber);
        if (bankAccountRepository.existsByUserIdAndBankCodeIgnoreCaseAndAccountNoHash(userId, bankCode, accountNoHash)) {
            log.warn("Duplicate bank account for userId={}, bankCode={}", userId, bankCode);
            throw new AppException(PaymentErrorCode.BANK_ACCOUNT_DUPLICATED);
        }

        String accountLast4 = accountNumber.substring(accountNumber.length() - 4);
        String accountNoEnc = encryptAccountNumber(accountNumber);
        Instant now = Instant.now();
        Instant cooldownUntil = now.plus(Duration.ofHours(DEFAULT_BANK_COOLDOWN_HOURS));

        BankAccount entity = BankAccount.builder()
                .userId(userId)
                .bankCode(bankCode)
                .bankName(bankName)
                .accountNoEnc(accountNoEnc)
                .accountNoHash(accountNoHash)
                .accountLast4(accountLast4)
                .holderName(normalizedHolderName)
                .verifiedAt(now)
                .cooldownUntil(cooldownUntil)
                .createdAt(now)
                .updatedAt(now)
                .build();

        BankAccount saved = bankAccountRepository.save(entity);
        log.info("Created bank account id={} for userId={}, bank={}, last4={}, cooldownUntil={}",
                saved.getId(), userId, bankCode, accountLast4, cooldownUntil);

        return mapToResponse(saved);
    }

    /**
     * Xóa tài khoản ngân hàng của tôi (UC-39, API-096).
     */
    @Transactional
    public void deleteBankAccount(Long userId, Long bankAccountId) {
        BankAccount account = bankAccountRepository.findByIdAndUserId(bankAccountId, userId)
                .orElseThrow(() -> new AppException(PaymentErrorCode.BANK_ACCOUNT_NOT_FOUND));

        bankAccountRepository.delete(account);
        log.info("Deleted bank account id={} for userId={}", bankAccountId, userId);
    }

    /**
     * Kiểm tra tài khoản ngân hàng đã liên kết có thuộc user và đã qua 24 giờ chờ (cooldown) chưa (UC-39 A2, UC-40 A3).
     */
    @Transactional(readOnly = true)
    public ResolvedBankDetails resolveAndValidateForWithdrawal(Long userId, Long bankAccountId) {
        BankAccount account = bankAccountRepository.findByIdAndUserId(bankAccountId, userId)
                .orElseThrow(() -> new AppException(PaymentErrorCode.BANK_ACCOUNT_NOT_FOUND));

        Instant now = Instant.now();
        if (account.getCooldownUntil() != null && now.isBefore(account.getCooldownUntil())) {
            log.warn("Withdrawal rejected: bankAccountId={} for userId={} is in 24h cooldown until {}",
                    bankAccountId, userId, account.getCooldownUntil());
            throw new AppException(PaymentErrorCode.BANK_ACCOUNT_COOLDOWN_ACTIVE);
        }

        String decryptedAccountNo = decryptAccountNumber(account.getAccountNoEnc(), account.getAccountLast4());
        return new ResolvedBankDetails(
                account.getId(),
                account.getBankCode(),
                account.getBankName(),
                decryptedAccountNo,
                account.getHolderName()
        );
    }

    /**
     * Chống lách quy tắc chờ 24 giờ khi người dùng nhập thủ công lại đúng số tài khoản ngân hàng vừa liên kết (UC-39, UC-40).
     */
    @Transactional(readOnly = true)
    public void validateNotInCooldownForManualEntry(Long userId, String bankName, String accountNumber) {
        if (userId == null || accountNumber == null || accountNumber.isBlank()) {
            return;
        }
        String normalizedAccountNo = accountNumber.trim().replaceAll("\\s+", "");
        String normalizedBank = normalizeName(bankName);
        Instant now = Instant.now();

        List<BankAccount> userAccounts = bankAccountRepository.findByUserIdOrderByCreatedAtDesc(userId);
        for (BankAccount acc : userAccounts) {
            if (acc.getCooldownUntil() == null || !now.isBefore(acc.getCooldownUntil())) {
                continue;
            }
            String decrypted = decryptAccountNumber(acc.getAccountNoEnc(), acc.getAccountLast4());
            if (!normalizedAccountNo.equals(decrypted)) {
                continue;
            }
            String storedBankNameNorm = normalizeName(acc.getBankName());
            String storedBankCodeNorm = normalizeName(acc.getBankCode());
            boolean bankMatches = normalizedBank.isEmpty()
                    || normalizedBank.equals(storedBankNameNorm)
                    || normalizedBank.equals(storedBankCodeNorm)
                    || storedBankNameNorm.contains(normalizedBank)
                    || normalizedBank.contains(storedBankNameNorm);
            if (bankMatches) {
                log.warn("Manual withdrawal rejected: userId={}, bank='{}', last4={} matches linked bankAccountId={} in 24h cooldown until {}",
                        userId, bankName, acc.getAccountLast4(), acc.getId(), acc.getCooldownUntil());
                throw new AppException(PaymentErrorCode.BANK_ACCOUNT_COOLDOWN_ACTIVE);
            }
        }
    }

    public record ResolvedBankDetails(
            Long bankAccountId,
            String bankCode,
            String bankName,
            String accountNumber,
            String holderName
    ) {
    }

    private String resolveApprovedKycFullName(Long userId) {
        if (kycRequestRepository != null) {
            Optional<KycRequest> latestKyc = kycRequestRepository.findFirstByUserIdOrderBySubmittedAtDescIdDesc(userId);
            if (latestKyc.isPresent()) {
                KycRequest kyc = latestKyc.get();
                if (kyc.getStatus() != KycStatus.APPROVED) {
                    throw new AppException(PaymentErrorCode.BANK_ACCOUNT_KYC_REQUIRED);
                }
                if (kyc.getFullName() != null && !kyc.getFullName().isBlank()) {
                    return kyc.getFullName();
                }
            } else {
                throw new AppException(PaymentErrorCode.BANK_ACCOUNT_KYC_REQUIRED);
            }
        }

        if (userProfileRepository != null) {
            Optional<UserProfile> profile = userProfileRepository.findById(userId);
            if (profile.isPresent() && profile.get().getFullName() != null && !profile.get().getFullName().isBlank()) {
                return profile.get().getFullName();
            }
        }

        throw new AppException(PaymentErrorCode.BANK_ACCOUNT_KYC_REQUIRED);
    }

    public static String normalizeName(String raw) {
        if (raw == null) {
            return "";
        }
        String withoutAccents = Normalizer.normalize(raw, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replace('đ', 'd')
                .replace('Đ', 'D');
        return withoutAccents.toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9\\s]", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private String resolveBankName(String bankCode, String customBankName) {
        if (customBankName != null && !customBankName.trim().isEmpty()) {
            return customBankName.trim();
        }
        return BANK_CODE_NAMES.getOrDefault(bankCode, bankCode);
    }

    public static String hashAccountNumber(String bankCode, String accountNumber) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String input = bankCode.trim().toUpperCase(Locale.ROOT) + ":" + accountNumber.trim();
            return HexFormat.of().formatHex(md.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public String encryptAccountNumber(String plainAccountNumber) {
        if (kycCrypto != null) {
            try {
                byte[] encrypted = kycCrypto.encrypt(
                        plainAccountNumber.getBytes(StandardCharsets.UTF_8),
                        "bank-account:v1"
                );
                if (encrypted != null && encrypted.length > 0) {
                    return Base64.getEncoder().encodeToString(encrypted);
                }
            } catch (Exception ignored) {
                // Fallback to built-in AES-256-GCM when app.kyc.encryption-keys is not set in local dev/test
            }
        }
        try {
            byte[] iv = new byte[12];
            SECURE_RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(resolveAesKey(), "AES"), new GCMParameterSpec(128, iv));
            byte[] ct = cipher.doFinal(plainAccountNumber.getBytes(StandardCharsets.UTF_8));
            return FALLBACK_PREFIX
                    + Base64.getEncoder().encodeToString(iv)
                    + ":"
                    + Base64.getEncoder().encodeToString(ct);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt bank account number", e);
        }
    }

    public String decryptAccountNumber(String encoded, String accountLast4) {
        if (encoded == null || encoded.isBlank()) {
            return "******" + (accountLast4 != null ? accountLast4 : "");
        }
        if (encoded.startsWith(FALLBACK_PREFIX)) {
            try {
                String[] parts = encoded.substring(FALLBACK_PREFIX.length()).split(":", 2);
                byte[] iv = Base64.getDecoder().decode(parts[0]);
                byte[] ct = Base64.getDecoder().decode(parts[1]);
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(resolveAesKey(), "AES"), new GCMParameterSpec(128, iv));
                return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
            } catch (Exception e) {
                log.warn("Fallback decrypt failed, returning masked last4: {}", e.getMessage());
                return "******" + (accountLast4 != null ? accountLast4 : "");
            }
        }
        if (kycCrypto != null) {
            try {
                byte[] decrypted = kycCrypto.decrypt(
                        Base64.getDecoder().decode(encoded),
                        "bank-account:v1"
                );
                if (decrypted != null) {
                    return new String(decrypted, StandardCharsets.UTF_8);
                }
            } catch (Exception e) {
                log.warn("KycCrypto decrypt failed, returning masked last4: {}", e.getMessage());
            }
        }
        return "******" + (accountLast4 != null ? accountLast4 : "");
    }

    private byte[] resolveAesKey() throws Exception {
        String secret = (vnPayConfig != null && vnPayConfig.getVnp_HashSecret() != null && !vnPayConfig.getVnp_HashSecret().isBlank())
                ? vnPayConfig.getVnp_HashSecret()
                : DEFAULT_SECRET_SEED;
        return MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
    }

    public BankAccountResponse mapToResponse(BankAccount entity) {
        Instant now = Instant.now();
        boolean ready = entity.getCooldownUntil() == null || !now.isBefore(entity.getCooldownUntil());
        String last4 = entity.getAccountLast4() != null ? entity.getAccountLast4() : "";

        return BankAccountResponse.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .bankCode(entity.getBankCode())
                .bankName(entity.getBankName())
                .maskedAccountNumber("******" + last4)
                .accountLast4(last4)
                .holderName(entity.getHolderName())
                .verifiedAt(entity.getVerifiedAt())
                .cooldownUntil(entity.getCooldownUntil())
                .readyForWithdrawal(ready)
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
