package com.fptpost.modules.identity.repository;

import com.fptpost.modules.identity.model.AccountDataRequest;
import com.fptpost.modules.identity.enums.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface AccountDataRequestRepository extends JpaRepository<AccountDataRequest, Long> {
    List<AccountDataRequest> findByUserIdOrderByRequestedAtDescIdDesc(Long userId);
    Optional<AccountDataRequest> findFirstByUserIdAndTypeAndStatusInOrderByRequestedAtDescIdDesc(Long userId, AccountRequestType type, Collection<AccountRequestStatus> statuses);

    Optional<AccountDataRequest> findByPublicId(UUID publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from AccountDataRequest r where r.publicId = :id")
    Optional<AccountDataRequest> findByPublicIdForUpdate(@Param("id") UUID publicId);

    @Query(value = """
            select r from AccountDataRequest r
            where (:status is null or r.status = :status) and (:type is null or r.type = :type)
            order by r.requestedAt asc, r.id asc
            """, countQuery = """
            select count(r) from AccountDataRequest r
            where (:status is null or r.status = :status) and (:type is null or r.type = :type)
            """)
    Page<AccountDataRequest> search(@Param("status") AccountRequestStatus status, @Param("type") AccountRequestType type,
                                    Pageable pageable);
}
