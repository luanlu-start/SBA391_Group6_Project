package com.fptpost.modules.chat.service;

import com.fptpost.common.exception.AppException;
import com.fptpost.modules.chat.dto.response.ChatRoomResponse;
import com.fptpost.modules.chat.exception.ChatErrorCode;
import com.fptpost.modules.chat.mapper.ChatMapper;
import com.fptpost.modules.chat.model.ChatRoom;
import com.fptpost.modules.chat.repository.ChatRoomRepository;
import com.fptpost.modules.chat.security.ChatAccessService;
import com.fptpost.modules.order.dto.OrderResponse;
import com.fptpost.modules.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import java.util.Objects;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatAccessService chatAccessService;
    private final ChatMapper chatMapper;
    private final OrderService orderService;
    private final ChatWritePolicy chatWritePolicy;

    public ChatRoomResponse openRoom(Long orderId, UUID userPublicId) {
        Long userId = chatAccessService.requireActiveUserId(userPublicId);
        OrderResponse order = orderService.getOrderById(orderId);

        boolean isSender = userId.equals(order.getSenderId());
        boolean isCourier = userId.equals(order.getCourierId());

        if (!isSender && !isCourier) {
            throw new AppException(ChatErrorCode.CHAT_ACCESS_DENIED);
        }

        if (order.getCourierId() == null) {
            throw new AppException(ChatErrorCode.CHAT_ORDER_NOT_ASSIGNED);
        }

        ChatRoom room = chatRoomRepository
                .findByOrderIdAndSenderIdAndCourierId(
                        order.getId(),
                        order.getSenderId(),
                        order.getCourierId()
                )
                .orElseGet(() -> createRoom(order));

        return toResponseWithWritePolicy(room, order);
    }

    public ChatRoomResponse getRoom(String roomId, UUID userPublicId) {
        Long userId = chatAccessService.requireActiveUserId(userPublicId);
        ChatRoom room = chatAccessService.requireParticipant(roomId, userId);

        OrderResponse order = orderService.getOrderById(room.getOrderId());
        return toResponseWithWritePolicy(room, order);
    }

    private ChatRoomResponse toResponseWithWritePolicy(
            ChatRoom room,
            OrderResponse order
    ) {
        Instant now = Instant.now();
        Instant orderDeadline = chatWritePolicy.getReadOnlyAt(order);
        Instant effectiveDeadline = room.getReadOnlyAt();

        // Áp dụng mốc sớm hơn nếu phòng đã có hạn khóa riêng.
        if (orderDeadline != null
                && (effectiveDeadline == null
                || orderDeadline.isBefore(effectiveDeadline))) {
            effectiveDeadline = orderDeadline;
        }

        boolean participantsChanged =
                !Objects.equals(room.getSenderId(), order.getSenderId())
                        || !Objects.equals(room.getCourierId(), order.getCourierId());

        boolean deadlineReached = effectiveDeadline != null
                && !now.isBefore(effectiveDeadline);

        ChatRoomResponse response = chatMapper.toRoomResponse(room);
        response.setReadOnlyAt(effectiveDeadline);
        response.setReadOnly(
                participantsChanged
                        || deadlineReached
                        || chatWritePolicy.isReadOnly(order, now)
        );

        return response;
    }

    private ChatRoom createRoom(OrderResponse order) {
        ChatRoom room = ChatRoom.builder()
                .orderId(order.getId())
                .senderId(order.getSenderId())
                .courierId(order.getCourierId())
                .createdAt(Instant.now())
                .build();

        try {
            return chatRoomRepository.insert(room);
        } catch (DuplicateKeyException exception) {
            // Hai bên có thể mở cùng một phòng đồng thời.
            return chatRoomRepository
                    .findByOrderIdAndSenderIdAndCourierId(
                            order.getId(),
                            order.getSenderId(),
                            order.getCourierId()
                    )
                    .orElseThrow(() -> exception);
        }
    }
}