package com.fptpost.modules.chat.service;

import com.fptpost.modules.chat.dto.response.ChatMessageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatMessagePublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public void publish(ChatMessageResponse message) {
        String destination = "/topic/chat-rooms/" + message.getRoomId();

        messagingTemplate.convertAndSend(destination, message);
    }
}