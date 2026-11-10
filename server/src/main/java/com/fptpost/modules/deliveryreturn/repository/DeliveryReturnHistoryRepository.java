package com.fptpost.modules.deliveryreturn.repository;

import com.fptpost.modules.deliveryreturn.model.DeliveryReturnHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DeliveryReturnHistoryRepository extends JpaRepository<DeliveryReturnHistory, Long> {
    List<DeliveryReturnHistory> findTop50ByOrderIdOrderByIdDesc(Long orderId);
}
