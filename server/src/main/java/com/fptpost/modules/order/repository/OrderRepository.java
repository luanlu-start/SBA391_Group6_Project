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

    java.util.List<Order> findBySenderIdOrderByCreatedAtDesc(Long senderId);

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

    @Query("""
        select o
        from Order o
        where o.status = 'FINDING_SHIPPER'
        and o.id = :id
    """)
    Optional<Order> findAvailableOrderById(Long id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.orderCode = :orderCode")
    Optional<Order> findByOrderCodeForUpdate(@org.springframework.data.repository.query.Param("orderCode") String orderCode);

    @Query("""
        select count(o)
        from Order o
        where o.courierId = :courierId
        and o.status in (:statuses)
    """)
    long countByCourierIdAndStatusIn(
            @org.springframework.data.repository.query.Param("courierId") Long courierId,
            @org.springframework.data.repository.query.Param("statuses") List<com.fptpost.modules.order.model.OrderStatus> statuses
    );

    @Query("""
        select sum(o.declaredValue)
        from Order o
        where o.courierId = :courierId
        and o.status in (:statuses)
    """)
    java.math.BigDecimal sumDeclaredValueByCourierIdAndStatusIn(
            @org.springframework.data.repository.query.Param("courierId") Long courierId,
            @org.springframework.data.repository.query.Param("statuses") List<com.fptpost.modules.order.model.OrderStatus> statuses
    );
}
