package com.fptpost.modules.rewards.repository;

import com.fptpost.modules.order.model.Order;
import com.fptpost.modules.rewards.model.Referral;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public interface RewardOrderQueries extends Repository<Order, Long> {
    @Query("""
            select count(o.id) from Order o
            where (o.senderId = :userId or o.courierId = :userId)
              and (o.status = COMPLETED or exists (
                select t.id from LedgerTransaction t
                where t.orderId = o.id and t.type = ESCROW_RELEASE and t.status = COMPLETED))
            """)
    long countCompletedOrders(Long userId);

    // The admin release endpoint confirms delivery. Complaint closures alone do not prove delivery.
    String ELIGIBLE = """
            from Order o join Escrow e on e.orderId = o.id
            where e.status = RELEASED and e.frozen = false and e.releaseAt is not null
              and (o.status = COMPLETED or (o.status in (ESCROWED, SHIPPER_ASSIGNED, IN_DELIVERY)
                and exists (select t.id from LedgerTransaction t
                  where t.orderId = o.id and t.type = ESCROW_RELEASE and t.refType = 'ESCROW')))
              and not exists (select t.id from LedgerTransaction t where t.orderId = o.id
                and t.type in (ESCROW_REFUND, COMPLAINT_CLAWBACK))
              and not exists (select c.id from Complaint c where c.orderId = o.id and c.unrecoveredAmount > 0)
            """;

    @Query("""
            select r from Referral r join fetch r.inviter join fetch r.invitee
            where r.rewardedAt is null and r.inviter.status = ACTIVE and r.invitee.status = ACTIVE
              and exists (select o.id
            """ + ELIGIBLE + """
              and e.releaseAt >= r.createdAt and (o.senderId = r.invitee.id or o.courierId = r.invitee.id))
            order by r.id
            """)
    List<Referral> findReadyReferrals(Pageable page);

    @Query("select count(o.id) " + ELIGIBLE + """
              and o.courierId = :userId and e.releaseAt >= :start and e.releaseAt < :end
            """)
    long countWeeklyDeliveries(Long userId, Instant start, Instant end);

    @Query("select o.courierId " + ELIGIBLE + """
              and e.releaseAt >= :start and e.releaseAt < :end
              and exists (select u.id from User u where u.id = o.courierId and u.status = ACTIVE)
              and not exists (select b.id from UserBadge b where b.user.id = o.courierId
                and b.code = :code and b.weekStart = :weekStart)
            group by o.courierId
            having count(o.id) >= :target
            order by o.courierId
            """)
    List<Long> findWeeklyRecipients(Instant start, Instant end, String code, LocalDate weekStart, long target,
                                    Pageable page);
}
