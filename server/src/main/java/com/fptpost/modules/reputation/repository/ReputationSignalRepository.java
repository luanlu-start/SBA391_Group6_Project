package com.fptpost.modules.reputation.repository;

import com.fptpost.modules.dispute.enums.ComplaintOutcome;
import com.fptpost.modules.order.model.Order;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/** Chỉ số đọc từ đơn hàng và khiếu nại để tính điểm uy tín; chỉ đọc. */
public interface ReputationSignalRepository extends Repository<Order, Long> {

    @Query("select o.courierId, count(o) from Order o where o.courierId in :ids "
            + "and o.status = com.fptpost.modules.order.model.OrderStatus.COMPLETED group by o.courierId")
    List<Object[]> completedOrders(@Param("ids") Collection<Long> courierIds);

    /** Khiếu nại đã đóng với phán quyết bất lợi cho người giao (hoàn toàn hoặc một phần). */
    @Query("select o.courierId, count(k) from Complaint k, Order o where o.id = k.orderId and o.courierId in :ids "
            + "and k.status = com.fptpost.modules.dispute.enums.ComplaintStatus.CLOSED and k.outcome in :outcomes "
            + "group by o.courierId")
    List<Object[]> lostComplaints(@Param("ids") Collection<Long> courierIds, @Param("outcomes") Collection<ComplaintOutcome> outcomes);
}
