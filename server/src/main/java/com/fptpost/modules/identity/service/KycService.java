package com.fptpost.modules.identity.service;

import com.fptpost.common.exception.*;
import com.fptpost.common.file.dto.ValidatedImage;
import com.fptpost.common.file.service.*;
import com.fptpost.modules.identity.config.KycProperties;
import com.fptpost.modules.identity.dto.request.KycSubmissionRequest;
import com.fptpost.modules.identity.dto.response.*;
import com.fptpost.modules.identity.enums.*;
import com.fptpost.modules.identity.exception.*;
import com.fptpost.modules.identity.mapper.KycMapper;
import com.fptpost.modules.identity.model.*;
import com.fptpost.modules.identity.repository.*;
import com.fptpost.modules.identity.security.KycCrypto;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.text.Normalizer;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service @RequiredArgsConstructor
public class KycService {
    private static final List<KycStatus> RESERVED = List.of(KycStatus.PENDING, KycStatus.APPROVED);
    private final IdentityUserAccess userAccess;
    private final UserRepository userRepository;
    private final UserProfileRepository profileRepository;
    private final KycRequestRepository requestRepository;
    private final KycFileRepository fileRepository;
    private final KycIdentityClaimRepository claimRepository;
    private final ConsentRepository consentRepository;
    private final IdentityAuditService auditService;
    private final ImageValidator imageValidator;
    private final PrivateFileStorage fileStorage;
    private final KycCrypto crypto;
    private final KycMapper kycMapper;
    private final KycProperties properties;
    private final Clock identityClock;
    private final PlatformTransactionManager transactions;

    @Transactional(readOnly = true)
    public KycStatusResponse status(UUID owner) { return snapshot(userAccess.read(owner)); }

    public KycStatusResponse submit(UUID owner, UUID session, KycSubmissionRequest input,
            MultipartFile front, MultipartFile back, MultipartFile portrait, String ip) {
        // Validate/decode in memory before holding SQL locks. No plaintext KYC file is written.
        var images = Map.of(KycFileType.ID_FRONT, imageValidator.validate(front), KycFileType.ID_BACK, imageValidator.validate(back),
                KycFileType.SELFIE_WITH_ID, imageValidator.validate(portrait));
        String hash = crypto.identityHash(input.getIdNumber());
        try {
            return new TransactionTemplate(transactions).execute(transaction -> submitLocked(owner, session, input, hash, images, ip));
        } catch (AppException exception) {
            if (exception.getErrorCode() == IdentityErrorCode.KYC_ID_DUPLICATED) auditService.record(owner, IdentityAuditAction.KYC_DUPLICATE_ID, null);
            throw exception;
        } catch (DataIntegrityViolationException exception) {
            // An absent reservation can be inserted concurrently; its PK is the final guard across instances.
            var existingOwner = claimRepository.activeOwner(hash, RESERVED);
            if (existingOwner.isPresent() && userRepository.findByPublicId(owner).map(user -> !user.getId().equals(existingOwner.get())).orElse(false)) {
                auditService.record(owner, IdentityAuditAction.KYC_DUPLICATE_ID, null);
                throw new AppException(IdentityErrorCode.KYC_ID_DUPLICATED);
            }
            throw exception;
        }
    }

    private KycStatusResponse submitLocked(UUID owner, UUID session, KycSubmissionRequest input, String hash,
            Map<KycFileType, ValidatedImage> images, String ip) {
        User user = userAccess.lock(owner, session);
        if (!Boolean.TRUE.equals(input.getConsentAccepted())) throw new AppException(GlobalErrorCode.INVALID_REQUEST);
        var profile = profileRepository.findById(user.getId()).orElseThrow();
        if (!properties.getPolicyVersion().equals(input.getPolicyVersion())) throw new AppException(IdentityErrorCode.KYC_POLICY_CHANGED);
        LocalDate today = LocalDate.ofInstant(identityClock.instant(), ZoneId.of("Asia/Ho_Chi_Minh"));
        if (profile.getDob() == null || !profile.getDob().equals(input.getDob()) || !name(profile.getFullName()).equals(name(input.getFullName())))
            throw new AppException(IdentityErrorCode.KYC_PROFILE_MISMATCH);
        if (input.getDob().plusYears(18).isAfter(today)) throw new AppException(IdentityErrorCode.PROFILE_AGE_INVALID);
        if (input.getIssuedDate().isAfter(today) || input.getIssuedDate().isBefore(input.getDob()) || input.getExpiryDate().isBefore(today)
                || input.getExpiryDate().isBefore(input.getIssuedDate())) throw new AppException(IdentityErrorCode.KYC_INVALID_DOCUMENT);
        var latest = requestRepository.findFirstByUserIdOrderBySubmittedAtDescIdDesc(user.getId());
        if (latest.isPresent() && RESERVED.contains(latest.get().getStatus())) throw new AppException(IdentityErrorCode.KYC_ALREADY_SUBMITTED);
        var recent = recent(user);
        if (recent.size() >= 3) {
            long wait = Duration.between(identityClock.instant(), recent.getFirst().getSubmittedAt().plus(Duration.ofDays(7))).toSeconds();
            throw new IdentityException(IdentityErrorCode.KYC_SUBMISSION_LIMIT, Math.max(1, wait));
        }
        var claim = claimRepository.lockByHash(hash).orElse(null);
        if (claim != null && RESERVED.contains(claim.getKyc().getStatus()) && !claim.getUser().getId().equals(user.getId()))
            throw new AppException(IdentityErrorCode.KYC_ID_DUPLICATED);
        UUID requestId = UUID.randomUUID();
        String encryptedId = Base64.getEncoder().encodeToString(crypto.encrypt(input.getIdNumber().getBytes(StandardCharsets.UTF_8), idContext(requestId, owner)));
        var request = requestRepository.saveAndFlush(KycRequest.builder().publicId(requestId).user(user).idNumberHash(hash).idNumberEnc(encryptedId)
                .idLastFour(input.getIdNumber().substring(8)).fullName(profile.getFullName()).dob(input.getDob()).issuedDate(input.getIssuedDate())
                .expiryDate(input.getExpiryDate()).status(KycStatus.PENDING).submittedAt(identityClock.instant()).consentVersion(input.getPolicyVersion())
                .consentIp(ip.length() <= 45 ? ip : "unknown").build());
        if (claim == null) claim = KycIdentityClaim.builder().idNumberHash(hash).build();
        claim.setUser(user); claim.setKyc(request); claimRepository.saveAndFlush(claim);
        for (var entry : images.entrySet()) {
            UUID fileId = UUID.randomUUID(); var image = entry.getValue();
            UUID storageKey = fileStorage.put(crypto.encrypt(image.bytes(), fileContext(fileId, requestId)));
            fileRepository.save(KycFile.builder().publicId(fileId).kyc(request).type(entry.getKey()).storageKey(storageKey)
                    .mediaType(image.mediaType()).sizeBytes(image.bytes().length).checksum(checksum(image.bytes())).build());
        }
        fileRepository.flush();
        if (!consentRepository.existsByUserIdAndPolicyTypeAndPolicyVersion(user.getId(), "KYC", input.getPolicyVersion())) {
            consentRepository.save(Consent.builder().user(user).policyType("KYC").policyVersion(input.getPolicyVersion())
                    .acceptedAt(identityClock.instant()).ip(request.getConsentIp()).build());
        }
        auditService.record(owner, IdentityAuditAction.KYC_SUBMITTED, requestId);
        return snapshot(user);
    }

    @Transactional(readOnly = true)
    public SignedFileLinkResponse fileLink(UUID owner, UUID session, UUID fileId) {
        var user = userAccess.read(owner);
        fileRepository.findByPublicIdAndKycUserId(fileId, user.getId()).orElseThrow(() -> new AppException(GlobalErrorCode.RESOURCE_NOT_FOUND));
        Instant expires = identityClock.instant().plusSeconds(300);
        String signature = crypto.sign(linkPayload(owner, session, fileId, expires.getEpochSecond()));
        return SignedFileLinkResponse.builder().url("/me/kyc/files/" + fileId + "?expires=" + expires.getEpochSecond() + "&signature=" + signature).expiresAt(expires).build();
    }

    @Transactional
    public ValidatedImage readFile(UUID owner, UUID session, UUID fileId, long expires, String signature) {
        long now = identityClock.instant().getEpochSecond();
        var user = userAccess.read(owner);
        var file = fileRepository.findByPublicIdAndKycUserId(fileId, user.getId()).orElseThrow(() -> new AppException(GlobalErrorCode.RESOURCE_NOT_FOUND));
        if (expires <= now || expires > now + 300 || !crypto.verify(linkPayload(owner, session, fileId, expires), signature))
            throw new AppException(IdentityErrorCode.KYC_FILE_LINK_INVALID);
        byte[] plaintext = crypto.decrypt(fileStorage.read(file.getStorageKey()), fileContext(fileId, file.getKyc().getPublicId()));
        if (!checksum(plaintext).equals(file.getChecksum())) throw new AppException(IdentityErrorCode.KYC_ENCRYPTION_UNAVAILABLE);
        auditService.record(owner, IdentityAuditAction.KYC_FILE_VIEWED, fileId);
        return new ValidatedImage(plaintext, file.getMediaType());
    }
    private KycStatusResponse snapshot(User user) {
        var latest = requestRepository.findFirstByUserIdOrderBySubmittedAtDescIdDesc(user.getId()); var recent = recent(user);
        var response = KycStatusResponse.builder().status(latest.map(KycRequest::getStatus).orElse(KycStatus.NOT_SUBMITTED))
                .policyVersion(properties.getPolicyVersion()).submissionsRemaining(Math.max(0, 3 - recent.size()))
                .nextSubmissionAt(recent.size() >= 3 ? recent.getFirst().getSubmittedAt().plus(Duration.ofDays(7)) : null).build();
        latest.ifPresent(request -> { var dto = kycMapper.toRequest(request); dto.setFiles(fileRepository.findByKycIdOrderByIdAsc(request.getId()).stream().map(kycMapper::toFile).toList()); response.setRequest(dto); });
        return response;
    }
    private List<KycRequest> recent(User user) { return requestRepository.findByUserIdAndSubmittedAtAfterOrderBySubmittedAtAscIdAsc(user.getId(), identityClock.instant().minus(Duration.ofDays(7))); }
    private String name(String value) { return Normalizer.normalize(value.strip().replaceAll("\\s+", " "), Normalizer.Form.NFC).toLowerCase(Locale.ROOT); }
    private String linkPayload(UUID owner, UUID session, UUID file, long expires) { return owner + ":" + session + ":" + file + ":" + expires; }
    private String idContext(UUID request, UUID owner) { return "kyc-id:" + request + ":" + owner; }
    private String fileContext(UUID file, UUID request) { return "kyc-file:" + file + ":" + request; }
    private String checksum(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
}
