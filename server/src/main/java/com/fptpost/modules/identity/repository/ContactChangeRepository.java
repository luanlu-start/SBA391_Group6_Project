package com.fptpost.modules.identity.repository;

import com.fptpost.modules.identity.model.ContactChange;
import com.fptpost.modules.identity.enums.ContactType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface ContactChangeRepository extends JpaRepository<ContactChange, Long> {
    Optional<ContactChange> findByPublicIdAndUserId(UUID publicId, Long userId);
    Optional<ContactChange> findFirstByUserIdAndTypeOrderByCreatedAtDescIdDesc(Long userId, ContactType type);
}
