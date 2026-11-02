package com.fptpost.modules.chat.service;

import com.fptpost.common.exception.AppException;
import com.fptpost.common.exception.GlobalErrorCode;
import com.fptpost.common.response.PageResponse;
import com.fptpost.modules.chat.dto.request.SendChatMessageRequest;
import com.fptpost.modules.chat.dto.response.ChatMessageResponse;
import com.fptpost.modules.chat.exception.ChatErrorCode;
import com.fptpost.modules.chat.mapper.ChatMapper;
import com.fptpost.modules.chat.model.ChatMessage;
import com.fptpost.modules.chat.model.ChatRoom;
import com.fptpost.modules.chat.repository.ChatMessageRepository;
import com.fptpost.modules.chat.security.ChatAccessService;
import com.fptpost.modules.order.dto.OrderResponse;
import com.fptpost.modules.order.service.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
@Validated
@RequiredArgsConstructor
public class ChatMessageService {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatAccessService chatAccessService;
    private final ChatMapper chatMapper;
    private final OrderService orderService;
    private final MongoTemplate mongoTemplate;
    private final ChatMessagePublisher chatMessagePublisher;
    private final ChatWritePolicy chatWritePolicy;

    public ChatMessageResponse sendMessage(
            String roomId,
            UUID userPublicId,
            @NotNull @Valid SendChatMessageRequest request
    ) {
        Long userId = chatAccessService.requireActiveUserId(userPublicId);
        ChatRoom room = chatAccessService.requireParticipant(roomId, userId);

        String clientMessageId = request.getClientMessageId().toString();
        String content = request.getContent().strip();

        ChatMessage existing = chatMessageRepository
                .findByRoomIdAndAuthorIdAndClientMessageId(
                        roomId, userId, clientMessageId
                )
                .orElse(null);

        if (existing != null) {
            return resolveRetry(existing, content);
        }

        Instant now = Instant.now();
        requireWritable(room, now);

        ChatMessage message = ChatMessage.builder()
                .roomId(roomId)
                .authorId(userId)
                .clientMessageId(clientMessageId)
                .content(content)
                .createdAt(now)
                .build();

        ChatMessage saved;

        try {
            saved = chatMessageRepository.insert(message);
        } catch (DuplicateKeyException exception) {
            ChatMessage duplicate = chatMessageRepository
                    .findByRoomIdAndAuthorIdAndClientMessageId(
                            roomId, userId, clientMessageId
                    )
                    .orElseThrow(() -> exception);

            return resolveRetry(duplicate, content);
        }

        updateLastMessageAt(saved);

        ChatMessageResponse response = chatMapper.toMessageResponse(saved);
        chatMessagePublisher.publish(response);

        return response;
    }

    public PageResponse<ChatMessageResponse> getHistory(
            String roomId,
            UUID userPublicId,
            int page,
            int size
    ) {
        Long userId = chatAccessService.requireActiveUserId(userPublicId);
        chatAccessService.requireParticipant(roomId, userId);

        if (page < 0 || size < 1 || size > 100) {
            throw new AppException(GlobalErrorCode.INVALID_REQUEST);
        }

        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")
                )
        );

        return PageResponse.from(
                chatMessageRepository.findByRoomId(roomId, pageable)
                        .map(chatMapper::toMessageResponse)
        );
    }

    private void requireWritable(ChatRoom room, Instant now) {
        OrderResponse order = orderService.getOrderById(room.getOrderId());

        boolean sameSender =
                Objects.equals(room.getSenderId(), order.getSenderId());
        boolean sameCourier =
                Objects.equals(room.getCourierId(), order.getCourierId());

        if (!sameSender || !sameCourier) {
            throw new AppException(ChatErrorCode.CHAT_ACCESS_DENIED);
        }

        boolean roomLocked = room.getReadOnlyAt() != null
                && !now.isBefore(room.getReadOnlyAt());

        if (roomLocked || chatWritePolicy.isReadOnly(order, now)) {
            throw new AppException(ChatErrorCode.CHAT_ROOM_READ_ONLY);
        }
    }

    private ChatMessageResponse resolveRetry(
            ChatMessage existing,
            String content
    ) {
        if (!Objects.equals(existing.getContent(), content)) {
            throw new AppException(GlobalErrorCode.INVALID_REQUEST);
        }

        updateLastMessageAt(existing);

        ChatMessageResponse response = chatMapper.toMessageResponse(existing);
        chatMessagePublisher.publish(response);

        return response;
    }

    private void updateLastMessageAt(ChatMessage message) {
        Query query = Query.query(
                Criteria.where("_id").is(message.getRoomId())
        );

        Update update = new Update()
                .max("lastMessageAt", message.getCreatedAt());

        mongoTemplate.updateFirst(query, update, ChatRoom.class);
    }
}