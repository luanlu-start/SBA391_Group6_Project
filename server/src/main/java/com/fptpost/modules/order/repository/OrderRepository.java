package com.fptpost.modules.order.repository;

import com.fptpost.modules.order.model.Order;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderCode(String orderCode);

    boolean existsByOrderCode(String orderCode);

    @Query("""
        select o
        from Order o
        where o.status = 'FINDING_SHIPPER'
    """)
    Page<Order> findAvailableShippingOrder(Pageable pageable);

    @Query("""
        select o
        from Order o
        where o.status = 'FINDING_SHIPPER'
        and lower(o.pickupAddress) like lower(concat('%', :keyword, '%'))
    """)
    Page<Order> findAvailableShippingOrderByKeyword(Pageable pageable, String keyword);

}
