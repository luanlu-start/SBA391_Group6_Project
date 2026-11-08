package com.fptpost.modules.reputation.repository;

import com.fptpost.modules.reputation.model.CourierCancellation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface CourierCancellationRepository extends JpaRepository<CourierCancellation, Long> {

    boolean existsByCourierIdAndOrderId(Long courierId, Long orderId);

    /** [courierId, số lần hủy, lần hủy gần nhất] kể từ {@code since}. */
    @Query("select c.courierId, count(c), max(c.cancelledAt) from CourierCancellation c "
            + "where c.courierId in :ids and c.cancelledAt >= :since group by c.courierId")
    List<Object[]> since(@Param("ids") Collection<Long> courierIds, @Param("since") Instant since);
}
