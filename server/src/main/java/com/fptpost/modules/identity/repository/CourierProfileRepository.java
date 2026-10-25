package com.fptpost.modules.identity.repository;

import com.fptpost.modules.identity.model.CourierProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CourierProfileRepository extends JpaRepository<CourierProfile, Long> {
    Optional<CourierProfile> findByUserId(Long userId);
    boolean existsByUserId(Long userId);
}
