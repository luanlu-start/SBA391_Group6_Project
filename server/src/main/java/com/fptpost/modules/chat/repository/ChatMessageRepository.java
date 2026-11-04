package com.fptpost.modules.chat.repository;

import com.fptpost.modules.chat.model.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface ChatMessageRepository
        extends MongoRepository<ChatMessage, String> {

    Page<ChatMessage> findByRoomId(String roomId, Pageable pageable);

    Optional<ChatMessage> findByRoomIdAndAuthorIdAndClientMessageId(
            String roomId,
            Long authorId,
            String clientMessageId
    );
}