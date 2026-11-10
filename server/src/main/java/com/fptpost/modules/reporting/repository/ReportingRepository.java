package com.fptpost.modules.reporting.repository;

import com.fptpost.modules.order.model.Order;
import com.fptpost.modules.payment.model.LedgerTransactionType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/** Truy vấn tổng hợp chỉ đọc cho dashboard và báo cáo (UC-59); không sửa dữ liệu nghiệp vụ. */
public interface ReportingRepository extends Repository<Order, Long> {

    /** Trạng thái đọc dạng chuỗi để báo cáo vẫn chạy khi module khác bổ sung trạng thái đơn mới. */
    @Query("select cast(o.status as String), count(o) from Order o where o.createdAt >= :from and o.createdAt < :to group by o.status")
    List<Object[]> countOrdersByStatus(@Param("from") Instant from, @Param("to") Instant to);

    @Query("select o.createdAt from Order o where o.createdAt >= :from and o.createdAt < :to")
    List<Instant> orderCreationTimes(@Param("from") Instant from, @Param("to") Instant to);

    @Query("select count(u) from User u where u.deleted = false and u.createdAt >= :from and u.createdAt < :to")
    long countNewUsers(@Param("from") Instant from, @Param("to") Instant to);

    @Query("select count(c) from CourierProfile c where c.registeredAt >= :from and c.registeredAt < :to")
    long countNewCouriers(@Param("from") Instant from, @Param("to") Instant to);

    @Query("select count(distinct o.courierId) from Order o where o.courierId is not null and o.updatedAt >= :from and o.updatedAt < :to")
    long countActiveCouriers(@Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select t.type, count(t), coalesce(sum(t.amount), 0) from LedgerTransaction t
            where t.type in :types and t.status = com.fptpost.modules.payment.model.LedgerTransactionStatus.COMPLETED
              and t.createdAt >= :from and t.createdAt < :to group by t.type
            """)
    List<Object[]> ledgerTotals(@Param("types") Collection<LedgerTransactionType> types,
                                @Param("from") Instant from, @Param("to") Instant to);

    /** Bút toán giải ngân ký quỹ đã hoàn tất: [createdBy (người giao), orderId, amount, createdAt]. */
    @Query("""
            select t.createdBy, t.orderId, t.amount, t.createdAt from LedgerTransaction t
            where t.type = com.fptpost.modules.payment.model.LedgerTransactionType.ESCROW_RELEASE
              and t.status = com.fptpost.modules.payment.model.LedgerTransactionStatus.COMPLETED
              and t.createdAt >= :from and t.createdAt < :to
            """)
    List<Object[]> escrowReleases(@Param("from") Instant from, @Param("to") Instant to);

    @Query("select o.id, o.feeRateLocked from Order o where o.id in :ids")
    List<Object[]> feeRates(@Param("ids") Collection<Long> ids);

    @Query("""
            select u.id, u.publicId, p.fullName, u.email from User u left join UserProfile p on p.userId = u.id where u.id in :ids
            """)
    List<Object[]> people(@Param("ids") Collection<Long> ids);

    @Query("""
            select o.orderCode, cast(o.status as String), o.productName, s.publicId, c.publicId, o.priceSuggested, o.priceAgreed, o.createdAt, o.endedAt
            from Order o left join User s on s.id = o.senderId left join User c on c.id = o.courierId
            where o.createdAt >= :from and o.createdAt < :to order by o.createdAt asc, o.id asc
            """)
    List<Object[]> ordersForExport(@Param("from") Instant from, @Param("to") Instant to);
}
