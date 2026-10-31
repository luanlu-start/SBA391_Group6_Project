package com.fptpost.modules.chat.service;

import com.fptpost.common.exception.AppException;
import com.fptpost.common.response.PageResponse;
import com.fptpost.modules.chat.dto.request.SendChatMessageRequest;
import com.fptpost.modules.chat.dto.response.ChatMessageResponse;
import com.fptpost.modules.chat.mapper.ChatMapper;
import com.fptpost.modules.chat.model.ChatMessage;
import com.fptpost.modules.chat.model.ChatRoom;
import com.fptpost.modules.chat.repository.ChatMessageRepository;
import com.fptpost.modules.chat.security.ChatAccessService;
import com.fptpost.modules.order.dto.OrderResponse;
import com.fptpost.modules.order.model.OrderStatus;
import com.fptpost.modules.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatMessageServiceTest {

    @Mock private ChatMessageRepository chatMessageRepository;
    @Mock private ChatAccessService chatAccessService;
    @Mock private ChatMapper chatMapper;
    @Mock private OrderService orderService;
    @Mock private MongoTemplate mongoTemplate;
    @Mock private ChatMessagePublisher chatMessagePublisher;
    @Mock private ChatWritePolicy chatWritePolicy;

    @InjectMocks private ChatMessageService service;

    private final UUID userPublicId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final String roomId = "room-1";
    private final Long userId = 1L;
    private ChatRoom room;
    private OrderResponse order;
    private SendChatMessageRequest request;

    @BeforeEach
    void setUp() {
        room = ChatRoom.builder()
                .id(roomId)
                .orderId(10L)
                .senderId(1L)
                .courierId(2L)
                .build();

        order = mock(OrderResponse.class);

        request = SendChatMessageRequest.builder()
                .clientMessageId(UUID.randomUUID())
                .content("Xin chao")
                .build();
    }

    private void allowParticipant() {
        when(chatAccessService.requireActiveUserId(userPublicId)).thenReturn(userId);
        when(chatAccessService.requireParticipant(roomId, userId)).thenReturn(room);
    }

    private void currentOrderParticipantsMatch() {
        when(orderService.getOrderById(10L)).thenReturn(order);
        when(order.getSenderId()).thenReturn(1L);
        when(order.getCourierId()).thenReturn(2L);
    }

    @Test
    void writableRoom_shouldInsertAndPublishMessage() {
        allowParticipant();
        currentOrderParticipantsMatch();

        ChatMessageResponse response = mock(ChatMessageResponse.class);

        when(chatMessageRepository.findByRoomIdAndAuthorIdAndClientMessageId(
                eq(roomId), eq(userId), anyString()))
                .thenReturn(Optional.empty());
        when(chatMessageRepository.insert(any(ChatMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(chatMapper.toMessageResponse(any(ChatMessage.class))).thenReturn(response);

        ChatMessageResponse result = service.sendMessage(roomId, userPublicId, request);

        assertSame(response, result);
        verify(chatMessageRepository).insert(
                org.mockito.ArgumentMatchers.<ChatMessage>argThat(message ->
                message.getRoomId().equals(roomId)
                        && message.getAuthorId().equals(userId)
                        && message.getContent().equals("Xin chao")));
        verify(chatMessagePublisher).publish(response);
        verify(mongoTemplate).updateFirst(any(
                org.springframework.data.mongodb.core.query.Query.class),
                any(org.springframework.data.mongodb.core.query.Update.class),
                eq(ChatRoom.class));
    }

    @Test
    void roomDeadlineReached_shouldRejectAndNotInsert() {
        allowParticipant();
        currentOrderParticipantsMatch();
        room.setReadOnlyAt(Instant.parse("2020-01-01T00:00:00Z"));

        assertThrows(AppException.class,
                () -> service.sendMessage(roomId, userPublicId, request));

        verify(chatMessageRepository, never()).insert(any(ChatMessage.class));
        verifyNoInteractions(chatMessagePublisher, mongoTemplate);
    }

    @Test
    void endedOrderAfter48Hours_shouldRejectAndNotInsert() {
        allowParticipant();
        currentOrderParticipantsMatch();
        when(chatWritePolicy.isReadOnly(eq(order), any(Instant.class)))
                .thenReturn(true);

        assertThrows(AppException.class,
                () -> service.sendMessage(roomId, userPublicId, request));

        verify(chatWritePolicy).isReadOnly(eq(order), any(Instant.class));
        verify(chatMessageRepository, never()).insert(any(ChatMessage.class));
        verifyNoInteractions(chatMessagePublisher, mongoTemplate);
    }

    @Test
    void participantsChanged_shouldRejectAndNotInsert() {
        allowParticipant();
        when(orderService.getOrderById(10L)).thenReturn(order);
        when(order.getSenderId()).thenReturn(1L);
        when(order.getCourierId()).thenReturn(3L);

        assertThrows(AppException.class,
                () -> service.sendMessage(roomId, userPublicId, request));

        verify(chatMessageRepository, never()).insert(any(ChatMessage.class));
        verifyNoInteractions(chatMessagePublisher, mongoTemplate);
    }

    @Test
    void outsider_shouldNotReadMessagesOrInsert() {
        when(chatAccessService.requireActiveUserId(userPublicId)).thenReturn(99L);
        when(chatAccessService.requireParticipant(roomId, 99L))
                .thenThrow(new AppException(
                        com.fptpost.modules.chat.exception.ChatErrorCode.CHAT_ACCESS_DENIED));

        assertThrows(AppException.class,
                () -> service.sendMessage(roomId, userPublicId, request));

        verifyNoInteractions(chatMessageRepository, chatMessagePublisher, mongoTemplate);
    }

    @Test
    void readOnlyRoom_shouldStillAllowHistory() {
        when(chatAccessService.requireActiveUserId(userPublicId)).thenReturn(userId);
        when(chatAccessService.requireParticipant(roomId, userId)).thenReturn(room);
        room.setReadOnlyAt(Instant.parse("2020-01-01T00:00:00Z"));

        Page<ChatMessage> page = new PageImpl<>(List.of());
        when(chatMessageRepository.findByRoomId(eq(roomId), any(Pageable.class)))
                .thenReturn(page);

        PageResponse<ChatMessageResponse> result =
                service.getHistory(roomId, userPublicId, 0, 20);

        assertNotNull(result);
        verify(chatMessageRepository).findByRoomId(eq(roomId), any(Pageable.class));
        verifyNoInteractions(orderService, chatWritePolicy);
    }

    @Test
    void duplicateRetryWithSameContent_shouldReturnExistingMessage() {
        allowParticipant();

        ChatMessage existing = ChatMessage.builder()
                .roomId(roomId)
                .authorId(userId)
                .clientMessageId(request.getClientMessageId().toString())
                .content(request.getContent())
                .createdAt(Instant.parse("2026-10-25T00:00:00Z"))
                .build();
        ChatMessageResponse response = mock(ChatMessageResponse.class);

        when(chatMessageRepository.findByRoomIdAndAuthorIdAndClientMessageId(
                roomId, userId, request.getClientMessageId().toString()))
                .thenReturn(Optional.of(existing));
        when(chatMapper.toMessageResponse(existing)).thenReturn(response);

        assertSame(response, service.sendMessage(roomId, userPublicId, request));
        verify(chatMessageRepository, never()).insert(any(ChatMessage.class));
        verify(chatMessagePublisher).publish(response);
        verifyNoInteractions(orderService, chatWritePolicy);
    }
}
