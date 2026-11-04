package com.fptpost.modules.identity.service;

import com.fptpost.common.exception.*;
import com.fptpost.modules.identity.config.AuthenticationProperties;
import com.fptpost.modules.identity.dto.request.AuthenticationRequest;
import com.fptpost.modules.identity.dto.response.*;
import com.fptpost.modules.identity.dto.internal.AuthenticationResult;
import com.fptpost.modules.identity.enums.UserStatus;
import com.fptpost.modules.identity.exception.*;
import com.fptpost.modules.identity.mapper.UserMapper;
import com.fptpost.modules.identity.mapper.SessionMapper;
import com.fptpost.modules.identity.model.*;
import com.fptpost.modules.identity.repository.*;
import com.fptpost.modules.identity.security.LoginAttemptLimiter;
import com.fptpost.modules.identity.util.ContactUtils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthenticationService {
    private final UserRepository userRepository;
    private final UserProfileRepository profileRepository;
    private final AuthSessionRepository sessionRepository;
    private final UserMapper userMapper;
    private final SessionMapper sessionMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationProperties properties;
    private final JwtTokenService jwtTokenService;
    private final OtpService otpService;
    private final LoginAttemptLimiter loginLimiter;
    private final Clock identityClock;
    private final SecureRandom identityRandom;
    private String dummyPasswordHash;

    @PostConstruct
    void initializeDummyHash() {
        dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }
    @Transactional(readOnly = true)
    public Long requireActiveUserId(UUID publicId) {
        if (publicId == null) {
            throw new AppException(GlobalErrorCode.UNAUTHORIZED);
        }

        User user = userRepository.findByPublicId(publicId)
                .orElseThrow(() ->
                        new AppException(GlobalErrorCode.UNAUTHORIZED)
                );

        requireActive(user);
        return user.getId();
    }
    @Transactional(noRollbackFor = IdentityException.class)
    public AuthenticationResult authenticate(AuthenticationRequest request, String ip, String userAgent,
            UUID browserId, String previousRefreshToken) {
        loginLimiter.check(ip);
        String identifier = request.getIdentifier().trim().toLowerCase(Locale.ROOT);
        if (identifier.matches("0[35789]\\d{8}")) identifier = ContactUtils.normalizePhone(identifier);
        Long id = userRepository.findIdByIdentifier(identifier).orElse(null);
        User user = id == null ? null : userRepository.findByIdForUpdate(id).orElse(null);
        Instant now = identityClock.instant();
        if (user != null && user.getLoginLockedUntil() != null) {
            if (now.isBefore(user.getLoginLockedUntil())) throw locked(user.getLoginLockedUntil(), now);
            user.setLoginLockedUntil(null);
            user.setFailedLoginCount(0);
        }
        boolean validLength = request.getPassword().getBytes(StandardCharsets.UTF_8).length <= 72;
        boolean correct = passwordEncoder.matches(validLength ? request.getPassword() : "invalid-long-password",
                user == null ? dummyPasswordHash : user.getPasswordHash());
        if (user == null || !validLength || !correct) {
            if (user != null) {
                user.setFailedLoginCount(user.getFailedLoginCount() + 1);
                if (user.getFailedLoginCount() >= properties.getMaxLoginAttempts()) {
                    user.setLoginLockedUntil(now.plusSeconds(properties.getLoginLockMinutes() * 60L));
                }
                userRepository.saveAndFlush(user);
            }
            loginLimiter.failure(ip);
            throw new IdentityException(IdentityErrorCode.LOGIN_FAILED);
        }
        if (!user.isDeleted() && user.getStatus() == UserStatus.PENDING_VERIFICATION) {
            // Only reveal the challenge after validating the password. Do not resend mail on each login.
            OtpChallengeResponse verification = otpService.registrationChallenge(user);
            user.setFailedLoginCount(0);
            user.setLoginLockedUntil(null);
            userRepository.saveAndFlush(user);
            loginLimiter.success(ip);
            throw new IdentityException(IdentityErrorCode.ACCOUNT_UNVERIFIED, verification);
        }
        requireActive(user);
        user.setFailedLoginCount(0);
        user.setLoginLockedUntil(null);
        userRepository.saveAndFlush(user);
        revokeBrowserSessions(user.getId(), browserId, previousRefreshToken, now);
        AuthenticationResult result = createSession(user, ip, userAgent, request.isRememberMe(), browserId);
        loginLimiter.success(ip);
        return result;
    }

    @Transactional(noRollbackFor = IdentityException.class)
    public AuthenticationResult refresh(String token, String ip, String userAgent, UUID browserId) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw invalidRefresh();
        String hash = hash(token);
        Long userId = sessionRepository.findUserIdByTokenHash(hash).orElseThrow(this::invalidRefresh);
        User user = userRepository.findByIdForUpdate(userId).orElseThrow(this::invalidRefresh);
        AuthSession old = sessionRepository.findByTokenHash(hash).orElseThrow(this::invalidRefresh);
        Instant now = identityClock.instant();
        if (!now.isBefore(old.getExpiresAt())) throw invalidRefresh();
        if (old.getReplacedBy() != null) {
            AuthSession latest = findLatestSession(userId, old.getPublicId()).orElseThrow(this::invalidRefresh);
            // A login/logout may have already retired this token family. Do not revoke unrelated sessions then.
            if (latest.getRevokedAt() != null || !now.isBefore(latest.getExpiresAt())) throw invalidRefresh();
            sessionRepository.revokeAll(userId, now);
            throw new IdentityException(IdentityErrorCode.REFRESH_REUSED);
        }
        if (old.getRevokedAt() != null) throw invalidRefresh();
        requireActive(user);
        return rotateSession(user, old, ip, userAgent, browserId);
    }

    @Transactional
    public AuthenticationResult renewAfterRoleChange(UUID owner, UUID sessionId, String ip, String userAgent) {
        User user = userRepository.findByPublicIdForUpdate(owner).orElseThrow(this::invalidRefresh);
        requireActive(user);
        AuthSession old = sessionRepository.findByPublicIdAndUserId(sessionId, user.getId()).orElseThrow(this::invalidRefresh);
        if (old.getRevokedAt() != null || old.getReplacedBy() != null || !identityClock.instant().isBefore(old.getExpiresAt())) throw invalidRefresh();
        return rotateSession(user, old, ip, userAgent, UUID.randomUUID());
    }

    private AuthenticationResult rotateSession(User user, AuthSession old, String ip, String userAgent, UUID browserId) {
        Instant now = identityClock.instant();
        old.setRevokedAt(now);
        UUID effectiveBrowserId = old.getBrowserId() == null ? browserId : old.getBrowserId();
        revokeBrowserSessions(user.getId(), effectiveBrowserId, null, now);
        AuthenticationResult result = createSession(user, ip, userAgent, old.isRememberMe(), effectiveBrowserId);
        String nextHash = hash(result.getRefreshToken());
        AuthSession next = sessionRepository.findByTokenHash(nextHash).orElseThrow();
        next.setCreatedAt(old.getCreatedAt());
        sessionRepository.save(next);
        old.setReplacedBy(next.getPublicId());
        sessionRepository.saveAndFlush(old);
        return result;
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> sessions(UUID userPublicId, UUID currentSessionId) {
        User user = userRepository.findByPublicId(userPublicId)
                .orElseThrow(() -> new AppException(GlobalErrorCode.UNAUTHORIZED));
        requireActive(user);
        UUID currentId = latestSession(user.getId(), currentSessionId).getPublicId();
        return sessionRepository.findByUserIdAndRevokedAtIsNullAndReplacedByIsNullAndExpiresAtAfterOrderByCreatedAtAscIdAsc(
                        user.getId(), identityClock.instant()).stream()
                .map(session -> sessionMapper.toResponse(session, session.getPublicId().equals(currentId)))
                .sorted(Comparator.comparing(SessionResponse::isCurrent).reversed()
                        .thenComparing(SessionResponse::getCreatedAt, Comparator.reverseOrder())
                        .thenComparing(SessionResponse::getId))
                .toList();
    }

    @Transactional
    public boolean revokeSession(UUID userPublicId, UUID sessionId, UUID currentSessionId) {
        User user = userRepository.findByPublicId(userPublicId)
                .orElseThrow(() -> new AppException(GlobalErrorCode.UNAUTHORIZED));
        user = userRepository.findByIdForUpdate(user.getId()).orElseThrow(this::invalidRefresh);
        requireActive(user);
        Instant now = identityClock.instant();
        AuthSession current = latestSession(user.getId(), currentSessionId);
        if (current.getRevokedAt() != null || !now.isBefore(current.getExpiresAt()))
            throw new AppException(GlobalErrorCode.UNAUTHORIZED);
        AuthSession target = latestSession(user.getId(), sessionId);
        if (target.getRevokedAt() != null || !now.isBefore(target.getExpiresAt()))
            throw new AppException(IdentityErrorCode.SESSION_NOT_FOUND);
        target.setRevokedAt(now);
        sessionRepository.saveAndFlush(target);
        return target.getPublicId().equals(current.getPublicId());
    }

    @Transactional
    public void logout(UUID userPublicId, UUID sessionId) {
        revokeSession(userPublicId, sessionId, sessionId);
    }

    @Scheduled(cron = "${app.authentication.session-cleanup-cron:0 0 * * * *}", zone = "UTC")
    @Transactional
    public void cleanupExpiredSessions() {
        Instant cutoff = identityClock.instant().minus(Duration.ofDays(properties.getExpiredSessionRetentionDays()));
        // Keep rotated/revoked tokens until expiry so reuse detection remains effective.
        int deleted = sessionRepository.deleteExpiredBefore(cutoff);
        if (deleted > 0) log.info("Deleted {} expired authentication session records", deleted);
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(UUID publicId) {
        User user = userRepository.findByPublicId(publicId).orElseThrow(() -> new AppException(GlobalErrorCode.UNAUTHORIZED));
        requireActive(user);
        return response(user);
    }

    private void revokeBrowserSessions(Long userId, UUID browserId, String previousRefreshToken, Instant now) {
        UUID previousId = null;
        if (previousRefreshToken != null && previousRefreshToken.matches("[A-Za-z0-9_-]{43}")) {
            AuthSession previous = sessionRepository.findByTokenHash(hash(previousRefreshToken)).orElse(null);
            if (previous != null && previous.getUser().getId().equals(userId)) {
                previousId = findLatestSession(userId, previous.getPublicId()).map(AuthSession::getPublicId).orElse(null);
            }
        }
        var active = sessionRepository.findByUserIdAndRevokedAtIsNullAndReplacedByIsNullAndExpiresAtAfterOrderByCreatedAtAscIdAsc(userId, now);
        for (AuthSession session : active) {
            if (browserId.equals(session.getBrowserId()) || session.getPublicId().equals(previousId)) {
                session.setRevokedAt(now);
                sessionRepository.save(session);
            }
        }
    }

    private AuthenticationResult createSession(User user, String ip, String userAgent, boolean rememberMe, UUID browserId) {
        Instant now = identityClock.instant();
        var active = sessionRepository.findByUserIdAndRevokedAtIsNullAndReplacedByIsNullAndExpiresAtAfterOrderByCreatedAtAscIdAsc(user.getId(), now);
        while (active.size() >= properties.getMaxDevices()) {
            AuthSession oldest = active.removeFirst();
            oldest.setRevokedAt(now);
            sessionRepository.save(oldest);
        }
        byte[] bytes = new byte[32];
        identityRandom.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String device = userAgent == null ? "Unknown device" : userAgent.replaceAll("\\p{Cntrl}", "");
        AuthSession session = AuthSession.builder().publicId(UUID.randomUUID()).user(user).tokenHash(hash(token)).browserId(browserId)
                .ip(ip.length() > 45 ? "unknown" : ip).deviceInfo(device.substring(0, Math.min(255, device.length())))
                .createdAt(now).expiresAt(now.plusSeconds(properties.getRefreshTtlDays() * 86400L)).rememberMe(rememberMe).build();
        sessionRepository.saveAndFlush(session);
        var jwt = jwtTokenService.issue(user, session);
        return new AuthenticationResult(AuthenticationResponse.builder().accessToken(jwt.getTokenValue()).tokenType("Bearer")
                .expiresAt(jwt.getExpiresAt()).user(response(user)).build(), token, rememberMe, browserId);
    }

    private UserResponse response(User user) {
        var profile = profileRepository.findById(user.getId()).orElseThrow(() -> new IllegalStateException("Missing user profile"));
        return userMapper.toUserResponse(user, profile);
    }

    private AuthSession latestSession(Long userId, UUID sessionId) {
        return findLatestSession(userId, sessionId).orElseThrow(() -> new AppException(IdentityErrorCode.SESSION_NOT_FOUND));
    }

    private Optional<AuthSession> findLatestSession(Long userId, UUID sessionId) {
        Set<UUID> visited = new HashSet<>();
        while (visited.add(sessionId)) {
            AuthSession session = sessionRepository.findByPublicIdAndUserId(sessionId, userId).orElse(null);
            if (session == null) return Optional.empty();
            if (session.getReplacedBy() == null) return Optional.of(session);
            // Resolve rotation under the user lock when revoking a device from an older list.
            sessionId = session.getReplacedBy();
        }
        throw new IllegalStateException("Cyclic authentication session rotation");
    }

    private void requireActive(User user) {
        if (user.isDeleted() || user.getStatus() == UserStatus.DELETED || user.getStatus() == UserStatus.LOCKED)
            throw new AppException(IdentityErrorCode.ACCOUNT_DISABLED);
        if (user.getStatus() != UserStatus.ACTIVE || user.getEmailVerifiedAt() == null)
            throw new AppException(IdentityErrorCode.ACCOUNT_UNVERIFIED);
    }

    private IdentityException locked(Instant until, Instant now) {
        return new IdentityException(IdentityErrorCode.LOGIN_LOCKED,
                Math.max(1, (Duration.between(now, until).toMillis() + 999) / 1000));
    }

    private AppException invalidRefresh() {
        return new AppException(IdentityErrorCode.REFRESH_INVALID);
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
