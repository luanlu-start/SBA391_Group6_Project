package com.fptpost.modules.identity.repository;

import com.fptpost.modules.identity.model.AccountDataRequest;
import com.fptpost.modules.identity.enums.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface AccountDataRequestRepository extends JpaRepository<AccountDataRequest, Long> {
    List<AccountDataRequest> findByUserIdOrderByRequestedAtDescIdDesc(Long userId);
    Optional<AccountDataRequest> findFirstByUserIdAndTypeAndStatusInOrderByRequestedAtDescIdDesc(Long userId, AccountRequestType type, Collection<AccountRequestStatus> statuses);
}
