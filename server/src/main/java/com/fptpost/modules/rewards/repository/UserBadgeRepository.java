package com.fptpost.modules.rewards.repository;

import com.fptpost.modules.rewards.model.UserBadge;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserBadgeRepository extends JpaRepository<UserBadge, Long> {
    List<UserBadge> findByUserIdOrderByAwardedAtDescIdDesc(Long userId, Pageable page);
}
