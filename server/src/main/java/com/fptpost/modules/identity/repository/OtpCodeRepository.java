package com.fptpost.modules.identity.repository;

import com.fptpost.modules.identity.enums.OtpType;
import com.fptpost.modules.identity.model.OtpCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface OtpCodeRepository extends JpaRepository<OtpCode, Long> {
    // Read only the owner ID before locking User, so OTP state is loaded after acquiring the lock.
    @Query("select o.user.id from OtpCode o where o.publicId = :publicId and o.type = :type")
    Optional<Long> findUserIdByChallenge(@Param("publicId") UUID publicId, @Param("type") OtpType type);

    Optional<OtpCode> findByPublicIdAndType(UUID publicId, OtpType type);

    Optional<OtpCode> findFirstByUserIdAndTypeOrderByCreatedAtDescIdDesc(Long userId, OtpType type);
}
