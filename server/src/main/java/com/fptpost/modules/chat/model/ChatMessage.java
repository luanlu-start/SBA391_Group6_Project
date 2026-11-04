package com.fptpost.modules.chat.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "chat_messages")
@CompoundIndex(
        name = "chat_message_history",
        def = "{'roomId': 1, 'createdAt': -1, '_id': -1}"
)
@CompoundIndex(
        name = "chat_message_retry_unique",
        def = "{'roomId': 1, 'authorId': 1, 'clientMessageId': 1}",
        unique = true
)
public class ChatMessage {

    @Id
    private String id;

    private String roomId;

    private Long authorId;

    private String clientMessageId;

    private String content;

    private Instant createdAt;
}