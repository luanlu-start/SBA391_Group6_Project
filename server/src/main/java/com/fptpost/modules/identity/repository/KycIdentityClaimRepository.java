package com.fptpost.modules.identity.repository;

import com.fptpost.modules.identity.model.KycIdentityClaim;
import com.fptpost.modules.identity.enums.KycStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface KycIdentityClaimRepository extends JpaRepository<KycIdentityClaim, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select c from KycIdentityClaim c where c.idNumberHash = :hash")
    Optional<KycIdentityClaim> lockByHash(@Param("hash") String hash);
    @Query("select c.user.id from KycIdentityClaim c where c.idNumberHash = :hash and c.kyc.status in :statuses")
    Optional<Long> activeOwner(@Param("hash") String hash, @Param("statuses") Collection<KycStatus> statuses);
}
