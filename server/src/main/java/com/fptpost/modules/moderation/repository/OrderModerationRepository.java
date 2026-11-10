package com.fptpost.modules.moderation.repository;

import com.fptpost.modules.moderation.enums.OrderModerationStatus;
import com.fptpost.modules.moderation.model.OrderModeration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderModerationRepository extends JpaRepository<OrderModeration, Long> {

    Optional<OrderModeration> findByOrderId(Long orderId);

    @Query(value = """
            select m from OrderModeration m, Order o
            where o.id = m.orderId and m.status = :status
              and (:pattern is null or lower(o.orderCode) like :pattern escape '!'
                   or lower(o.productName) like :pattern escape '!')
            order by m.submittedAt asc, m.id asc
            """,
            countQuery = """
            select count(m) from OrderModeration m, Order o
            where o.id = m.orderId and m.status = :status
              and (:pattern is null or lower(o.orderCode) like :pattern escape '!'
                   or lower(o.productName) like :pattern escape '!')
            """)
    Page<OrderModeration> search(@Param("status") OrderModerationStatus status, @Param("pattern") String pattern,
                                 Pageable pageable);

    long countBySenderIdAndStatusAndReviewedAtGreaterThanEqual(Long senderId, OrderModerationStatus status, Instant since);

    @Query("""
            select m.senderId, count(m) from OrderModeration m
            where m.senderId in :senderIds and m.status = :status and m.reviewedAt >= :since
            group by m.senderId
            """)
    List<Object[]> countBySenders(@Param("senderIds") Collection<Long> senderIds,
                                  @Param("status") OrderModerationStatus status, @Param("since") Instant since);
}
