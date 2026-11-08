package com.fptpost.modules.identity.repository;

import com.fptpost.modules.identity.model.Consent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConsentRepository extends JpaRepository<Consent, Long> {
    boolean existsByUserIdAndPolicyTypeAndPolicyVersion(Long userId, String policyType, String policyVersion);

    List<Consent> findByUserIdOrderByAcceptedAtAsc(Long userId);
}
