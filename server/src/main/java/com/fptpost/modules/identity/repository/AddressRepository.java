package com.fptpost.modules.identity.repository;

import com.fptpost.modules.identity.model.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface AddressRepository extends JpaRepository<Address, Long> {
    List<Address> findByUserIdOrderByIdAsc(Long userId);
    Optional<Address> findByPublicIdAndUserId(UUID publicId, Long userId);
}
