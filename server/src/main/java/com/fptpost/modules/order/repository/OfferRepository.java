package com.fptpost.modules.order.repository;

import com.fptpost.modules.order.model.Offer;
import com.fptpost.modules.order.model.OfferStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface OfferRepository extends JpaRepository<Offer, Long> {

    List<Offer> findByOrderIdOrderByCreatedAtDesc(Long orderId);

    List<Offer> findByOrderIdAndStatusInOrderByCreatedAtDesc(Long orderId, Collection<OfferStatus> statuses);

    List<Offer> findByCourierIdOrderByCreatedAtDesc(Long courierId);

    @Query("SELECT o FROM Offer o WHERE o.order.id = :orderId AND o.courier.id = :courierId AND o.status IN :statuses")
    List<Offer> findByOrderIdAndCourierIdAndStatusIn(
            @Param("orderId") Long orderId,
            @Param("courierId") Long courierId,
            @Param("statuses") Collection<OfferStatus> statuses
    );

    @Query("SELECT o FROM Offer o WHERE o.order.id = :orderId AND o.courier.id = :courierId ORDER BY o.createdAt DESC")
    List<Offer> findByOrderIdAndCourierId(
            @Param("orderId") Long orderId,
            @Param("courierId") Long courierId
    );
}
