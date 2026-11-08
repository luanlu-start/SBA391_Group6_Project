package com.fptpost.modules.jobs.repository;

import com.fptpost.modules.order.model.Order;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/** Truy vấn bảo trì đơn hàng cho tác vụ nền; chỉ chuyển trạng thái đã hết hạn nên chạy lại không gây trùng. */
public interface OrderMaintenanceRepository extends Repository<Order, Long> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Offer o set o.status = com.fptpost.modules.order.model.OfferStatus.EXPIRED, o.updatedAt = :now
            where o.status = com.fptpost.modules.order.model.OfferStatus.PENDING and o.expiresAt < :now
            """)
    int expireOffers(@Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Invitation i set i.status = com.fptpost.modules.order.model.InvitationStatus.EXPIRED, i.updatedAt = :now
            where i.status = com.fptpost.modules.order.model.InvitationStatus.PENDING and i.expiresAt < :now
            """)
    int expireInvitations(@Param("now") Instant now);

    @Query("""
            select o from Order o
            where o.status = com.fptpost.modules.order.model.OrderStatus.FINDING_SHIPPER and o.courierId is null
              and o.createdAt < :openedBefore
            order by o.createdAt asc, o.id asc
            """)
    List<Order> findStaleOpenOrders(@Param("openedBefore") Instant openedBefore, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Order o set o.status = com.fptpost.modules.order.model.OrderStatus.EXPIRED, o.endedAt = :now, o.updatedAt = :now
            where o.id in :ids and o.status = com.fptpost.modules.order.model.OrderStatus.FINDING_SHIPPER and o.courierId is null
            """)
    int expireOrders(@Param("ids") Collection<Long> ids, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Offer o set o.status = com.fptpost.modules.order.model.OfferStatus.EXPIRED, o.updatedAt = :now
            where o.order.id in :ids and o.status = com.fptpost.modules.order.model.OfferStatus.PENDING
            """)
    int expireOffersOfOrders(@Param("ids") Collection<Long> ids, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Invitation i set i.status = com.fptpost.modules.order.model.InvitationStatus.EXPIRED, i.updatedAt = :now
            where i.order.id in :ids and i.status = com.fptpost.modules.order.model.InvitationStatus.PENDING
            """)
    int expireInvitationsOfOrders(@Param("ids") Collection<Long> ids, @Param("now") Instant now);
}
