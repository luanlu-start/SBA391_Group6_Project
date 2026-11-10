package com.fptpost.modules.dispute.config;

import com.fptpost.modules.identity.model.User;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/** Tra tài khoản demo và đơn demo cho {@link DisputeDemoSeeder}. */
public interface DemoAccountLookup extends Repository<User, Long> {

    @Query("select u.id from User u where u.email = :email and u.deleted = false")
    Optional<Long> idByEmail(@Param("email") String email);

    @Query("select count(o) from Order o where o.orderCode like :prefix")
    long countOrders(@Param("prefix") String prefix);

    @Query("""
            select count(c) from Complaint c, Order o where o.id = c.orderId and o.orderCode like :prefix
            and c.status in (com.fptpost.modules.dispute.enums.ComplaintStatus.OPEN,
                             com.fptpost.modules.dispute.enums.ComplaintStatus.INVESTIGATING,
                             com.fptpost.modules.dispute.enums.ComplaintStatus.AWAITING_INFO)
            """)
    long countActiveComplaints(@Param("prefix") String prefix);
}
