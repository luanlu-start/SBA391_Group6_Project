package com.fptpost.modules.identity.repository;

import com.fptpost.modules.identity.model.IdentityAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdentityAuditLogRepository extends JpaRepository<IdentityAuditLog, Long> {}
