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
@Document(collection = "chat_rooms")
@CompoundIndex(
        name = "chat_room_participants_unique",
        def = "{'orderId': 1, 'senderId': 1, 'courierId': 1}",
        unique = true
)
public class ChatRoom {

    @Id
    private String id;

    private Long orderId;

    private Long senderId;

    private Long courierId;

    private Instant createdAt;

    private Instant lastMessageAt;

    private Instant readOnlyAt;
}