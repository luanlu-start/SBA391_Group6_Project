package com.fptpost.modules.order.repository;

import com.fptpost.modules.order.model.FavoriteCourier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FavoriteCourierRepository extends JpaRepository<FavoriteCourier, Long> {

    @Query("SELECT fc FROM FavoriteCourier fc WHERE fc.sender.id = :senderId ORDER BY fc.createdAt DESC")
    List<FavoriteCourier> findBySenderIdOrderByCreatedAtDesc(@Param("senderId") Long senderId);

    @Query("SELECT fc FROM FavoriteCourier fc WHERE fc.sender.id = :senderId AND fc.courier.id = :courierId")
    Optional<FavoriteCourier> findBySenderIdAndCourierId(@Param("senderId") Long senderId, @Param("courierId") Long courierId);

    @Query("SELECT CASE WHEN COUNT(fc) > 0 THEN TRUE ELSE FALSE END FROM FavoriteCourier fc WHERE fc.sender.id = :senderId AND fc.courier.id = :courierId")
    boolean existsBySenderIdAndCourierId(@Param("senderId") Long senderId, @Param("courierId") Long courierId);

    @Modifying
    @Query("DELETE FROM FavoriteCourier fc WHERE fc.sender.id = :senderId AND fc.courier.id = :courierId")
    void deleteBySenderIdAndCourierId(@Param("senderId") Long senderId, @Param("courierId") Long courierId);

    @Query("SELECT fc.courier.id FROM FavoriteCourier fc WHERE fc.sender.id = :senderId")
    List<Long> findCourierIdsBySenderId(@Param("senderId") Long senderId);
}
