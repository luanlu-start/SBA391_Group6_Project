package com.fptpost.modules.identity.service;

import com.fptpost.modules.identity.enums.IdentityAuditAction;
import com.fptpost.modules.identity.model.IdentityAuditLog;
import com.fptpost.modules.identity.repository.IdentityAuditLogRepository;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class IdentityAuditService {
    private final IdentityAuditLogRepository auditRepository;
    private final Clock identityClock;
    @Transactional
    public void record(UUID actor, IdentityAuditAction action, UUID target) {
        auditRepository.saveAndFlush(IdentityAuditLog.builder().actorPublicId(actor).action(action).targetPublicId(target).occurredAt(identityClock.instant()).build());
    }
}
