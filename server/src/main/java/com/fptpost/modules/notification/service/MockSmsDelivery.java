package com.fptpost.modules.notification.service;

import com.fptpost.modules.notification.model.MockSmsMessage;
import com.fptpost.modules.notification.repository.MockSmsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;

@Service @RequiredArgsConstructor
public class MockSmsDelivery {
    private final MockSmsRepository messages;
    private final Clock clock;

    @Transactional
    public void send(NotificationDeliveryClaim claim, String phone) {
        if (messages.existsById(claim.id())) return;
        messages.saveAndFlush(MockSmsMessage.builder().id(claim.id()).recipient(claim.event().getRecipient())
                .phoneLastFour(phone.substring(phone.length() - 4)).body(claim.event().getBody()).at(clock.instant()).build());
    }
}
