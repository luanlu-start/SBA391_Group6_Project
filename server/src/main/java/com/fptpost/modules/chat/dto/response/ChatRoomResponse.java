package com.fptpost.modules.chat.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ChatRoomResponse {

    String id;

    Long orderId;

    Long senderId;

    Long courierId;

    Instant createdAt;

    Instant lastMessageAt;

    Instant readOnlyAt;
}