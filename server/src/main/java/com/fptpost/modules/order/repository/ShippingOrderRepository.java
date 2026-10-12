package com.fptpost.modules.order.repository;

import com.fptpost.modules.order.model.ShippingOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ShippingOrderRepository extends JpaRepository<ShippingOrder, Long> {

    Optional<ShippingOrder> findByOrderCode(String orderCode);

    boolean existsByOrderCode(String orderCode);
}
