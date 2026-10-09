package com.fptpost.modules.identity.repository;

import com.fptpost.modules.identity.model.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {}
