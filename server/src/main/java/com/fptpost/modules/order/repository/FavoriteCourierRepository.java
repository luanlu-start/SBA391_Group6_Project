package com.fptpost.modules.order.repository;

import com.fptpost.modules.order.model.FavoriteCourier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FavoriteCourierRepository extends JpaRepository<FavoriteCourier, Long> {

    List<FavoriteCourier> findBySenderIdOrderByCreatedAtDesc(Long senderId);

    Optional<FavoriteCourier> findBySenderIdAndCourierId(Long senderId, Long courierId);

    boolean existsBySenderIdAndCourierId(Long senderId, Long courierId);

    void deleteBySenderIdAndCourierId(Long senderId, Long courierId);

    @Query("SELECT fc.courier.id FROM FavoriteCourier fc WHERE fc.sender.id = :senderId")
    List<Long> findCourierIdsBySenderId(@Param("senderId") Long senderId);
}
