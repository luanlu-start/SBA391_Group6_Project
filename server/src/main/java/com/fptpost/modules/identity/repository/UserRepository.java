package com.fptpost.modules.identity.repository;

import com.fptpost.modules.identity.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    Optional<User> findByPublicId(UUID publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.publicId = :publicId")
    Optional<User> findByPublicIdForUpdate(@Param("publicId") UUID publicId);

    @Query("select u.id from User u where u.email = :identifier or u.phone = :identifier")
    Optional<Long> findIdByIdentifier(@Param("identifier") String identifier);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);
}
