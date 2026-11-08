package com.fptpost.modules.relay.repository;

import com.fptpost.modules.relay.model.HandoverToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface HandoverTokenRepository extends JpaRepository<HandoverToken, Long> {

    Optional<HandoverToken> findTopByOrderIdAndLegNoAndIsUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
            Long orderId, Integer legNo, Instant now);

    Optional<HandoverToken> findByTokenCode(String tokenCode);

    Optional<HandoverToken> findByTokenCodeAndIsUsedFalse(String tokenCode);
}
