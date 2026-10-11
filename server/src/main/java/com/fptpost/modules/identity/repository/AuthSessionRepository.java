package com.fptpost.modules.identity.repository;

import com.fptpost.modules.identity.model.AuthSession;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.*;

public interface AuthSessionRepository extends JpaRepository<AuthSession, Long> {
    @Query("select s.user.id from AuthSession s where s.tokenHash = :hash")
    Optional<Long> findUserIdByTokenHash(@Param("hash") String hash);

    Optional<AuthSession> findByTokenHash(String hash);

    @EntityGraph(attributePaths = {"user", "user.roles"})
    Optional<AuthSession> findByPublicId(UUID publicId);

    List<AuthSession> findByUserIdAndRevokedAtIsNullAndReplacedByIsNullAndExpiresAtAfterOrderByCreatedAtAscIdAsc(Long userId, Instant now);

    @Modifying
    @Query("update AuthSession s set s.revokedAt = :now, s.version = s.version + 1 where s.user.id = :userId and s.revokedAt is null")
    void revokeAll(@Param("userId") Long userId, @Param("now") Instant now);
}
