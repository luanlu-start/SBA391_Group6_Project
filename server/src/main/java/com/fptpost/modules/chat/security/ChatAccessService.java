package com.fptpost.modules.chat.security;

import com.fptpost.common.exception.AppException;
import com.fptpost.modules.chat.exception.ChatErrorCode;
import com.fptpost.modules.chat.model.ChatRoom;
import com.fptpost.modules.chat.repository.ChatRoomRepository;
import com.fptpost.modules.identity.service.AuthenticationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatAccessService {

    private final AuthenticationService authenticationService;
    private final ChatRoomRepository chatRoomRepository;

    public Long requireActiveUserId(UUID userPublicId) {
        return authenticationService.requireActiveUserId(userPublicId);
    }

    public ChatRoom requireParticipant(String roomId, Long userId) {
        if (userId == null) {
            throw new AppException(ChatErrorCode.CHAT_ACCESS_DENIED);
        }

        if (roomId == null || roomId.isBlank()) {
            throw new AppException(ChatErrorCode.CHAT_ROOM_NOT_FOUND);
        }

        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() ->
                        new AppException(ChatErrorCode.CHAT_ROOM_NOT_FOUND)
                );

        boolean isSender = userId.equals(room.getSenderId());
        boolean isCourier = userId.equals(room.getCourierId());

        if (!isSender && !isCourier) {
            throw new AppException(ChatErrorCode.CHAT_ACCESS_DENIED);
        }

        return room;
    }
}