package com.fptpost.modules.order.repository;

import com.fptpost.modules.order.model.OrderLeg;
import com.fptpost.modules.order.model.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderLegRepository extends JpaRepository<OrderLeg, Long> {

    List<OrderLeg> findByOrderIdOrderByLegNoAsc(Long orderId);

    List<OrderLeg> findByStatus(OrderStatus status);
}
