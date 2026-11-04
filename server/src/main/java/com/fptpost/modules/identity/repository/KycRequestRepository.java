package com.fptpost.modules.identity.repository;

import com.fptpost.modules.identity.model.KycRequest;
import com.fptpost.modules.identity.enums.KycStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;

public interface KycRequestRepository extends JpaRepository<KycRequest, Long> {
    Optional<KycRequest> findFirstByUserIdOrderBySubmittedAtDescIdDesc(Long userId);
    List<KycRequest> findByUserIdAndSubmittedAtAfterOrderBySubmittedAtAscIdAsc(Long userId, Instant since);
    boolean existsByUserIdAndStatusIn(Long userId, Collection<KycStatus> statuses);
    @Query("select k from KycRequest k join fetch k.user left join fetch k.reviewer where k.publicId = :id")
    Optional<KycRequest> findByPublicId(@Param("id") UUID publicId);

    @Query("select k.user.publicId from KycRequest k where k.publicId = :id")
    Optional<UUID> findApplicantPublicId(@Param("id") UUID publicId);

    @Query("select k.idNumberHash from KycRequest k where k.publicId = :id")
    Optional<String> findIdentityHash(@Param("id") UUID publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select k from KycRequest k where k.publicId = :id")
    Optional<KycRequest> findByPublicIdForUpdate(@Param("id") UUID publicId);
}
