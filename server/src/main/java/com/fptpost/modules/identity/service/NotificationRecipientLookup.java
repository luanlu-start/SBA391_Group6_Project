package com.fptpost.modules.identity.service;

import com.fptpost.common.exception.*;
import com.fptpost.modules.identity.enums.UserStatus;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.Optional;
import java.util.UUID;

/** Internal contact projection for notification delivery; never exposed as a directory endpoint. */
@Service @RequiredArgsConstructor
public class NotificationRecipientLookup {
    private final UserRepository users;

    @Transactional(readOnly = true)
    public Optional<Recipient> find(UUID publicId) { return users.findByPublicId(publicId).map(this::project); }

    /** Serializes preference updates and event deduplication across processes for the same recipient. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Recipient lock(UUID publicId) {
        return project(users.findByPublicIdForUpdate(publicId)
                .orElseThrow(() -> new AppException(GlobalErrorCode.RESOURCE_NOT_FOUND)));
    }

    private Recipient project(User user) {
        return new Recipient(user.getPublicId(), user.getEmail(), user.getPhone(), user.getEmailVerifiedAt() != null,
                user.getPhoneVerifiedAt() != null, user.getStatus(), user.isDeleted());
    }

    public record Recipient(UUID publicId, String email, String phone, boolean emailVerified,
            boolean phoneVerified, UserStatus status, boolean deleted) {
        public boolean canReceive() { return !deleted && status != UserStatus.DELETED; }
    }
}
