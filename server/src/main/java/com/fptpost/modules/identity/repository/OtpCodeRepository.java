package com.fptpost.modules.identity.repository;

import com.fptpost.modules.identity.model.OtpCode;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OtpCodeRepository extends JpaRepository<OtpCode, Long> {}
