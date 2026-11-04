package com.fptpost.modules.payment.repository;

import com.fptpost.modules.payment.model.Escrow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EscrowRepository extends JpaRepository<Escrow, Long> {

    Optional<Escrow> findByOrderId(Long orderId);

    boolean existsByOrderId(Long orderId);

    List<Escrow> findBySenderId(Long senderId);
}
