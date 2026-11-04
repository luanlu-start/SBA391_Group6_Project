package com.fptpost.modules.notification.repository;

import com.fptpost.modules.notification.model.MockSmsMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface MockSmsRepository extends JpaRepository<MockSmsMessage, UUID> { }
