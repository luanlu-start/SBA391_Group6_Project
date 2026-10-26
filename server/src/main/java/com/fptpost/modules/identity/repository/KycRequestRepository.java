package com.fptpost.modules.identity.repository;

import com.fptpost.modules.identity.model.KycRequest;
import com.fptpost.modules.identity.enums.KycStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.*;

public interface KycRequestRepository extends JpaRepository<KycRequest, Long> {
    Optional<KycRequest> findFirstByUserIdOrderBySubmittedAtDescIdDesc(Long userId);
    List<KycRequest> findByUserIdAndSubmittedAtAfterOrderBySubmittedAtAscIdAsc(Long userId, Instant since);
    boolean existsByUserIdAndStatusIn(Long userId, Collection<KycStatus> statuses);
}
