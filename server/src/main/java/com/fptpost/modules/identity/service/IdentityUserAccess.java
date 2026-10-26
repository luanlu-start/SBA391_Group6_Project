package com.fptpost.modules.identity.service;

import com.fptpost.common.exception.*;
import com.fptpost.modules.identity.enums.UserStatus;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.repository.*;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class IdentityUserAccess {
    private final UserRepository userRepository;
    private final AuthSessionRepository sessionRepository;
    private final Clock identityClock;
    public User read(UUID publicId) {
        return active(userRepository.findByPublicId(publicId).orElseThrow(this::unauthorized));
    }
    public User lock(UUID publicId, UUID sessionId) {
        User user = active(userRepository.findByPublicIdForUpdate(publicId).orElseThrow(this::unauthorized));
        var session = sessionRepository.findByPublicIdAndUserId(sessionId, user.getId()).orElseThrow(this::unauthorized);
        if (session.getRevokedAt() != null || session.getReplacedBy() != null || !identityClock.instant().isBefore(session.getExpiresAt()))
            throw unauthorized();
        return user;
    }
    private User active(User user) {
        if (user.isDeleted() || user.getStatus() != UserStatus.ACTIVE || user.getEmailVerifiedAt() == null) throw unauthorized();
        return user;
    }
    private AppException unauthorized() { return new AppException(GlobalErrorCode.UNAUTHORIZED); }
}
