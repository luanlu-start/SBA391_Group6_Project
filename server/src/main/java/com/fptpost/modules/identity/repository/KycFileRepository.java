package com.fptpost.modules.identity.repository;

import com.fptpost.modules.identity.model.KycFile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface KycFileRepository extends JpaRepository<KycFile, Long> {
    List<KycFile> findByKycIdOrderByIdAsc(Long kycId);
    Optional<KycFile> findByPublicIdAndKycUserId(UUID publicId, Long userId);
    Optional<KycFile> findByPublicIdAndKycPublicId(UUID publicId, UUID requestPublicId);
}
