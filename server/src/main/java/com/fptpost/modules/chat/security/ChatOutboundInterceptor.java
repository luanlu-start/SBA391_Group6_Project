package com.fptpost.modules.chat.security;

import com.fptpost.common.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ChatOutboundInterceptor implements ChannelInterceptor {

    private final ChatSocketSessions chatSocketSessions;
    private final ChatAccessService chatAccessService;
    private final JwtDecoder jwtDecoder;

    @Override
    public Message<?> preSend(
            Message<?> message,
            MessageChannel channel
    ) {
        SimpMessageHeaderAccessor accessor =
                SimpMessageHeaderAccessor.wrap(message);

        if (accessor.getMessageType() != SimpMessageType.MESSAGE) {
            return message;
        }

        Jwt storedJwt = chatSocketSessions.find(accessor.getSessionId());

        if (storedJwt == null
                || storedJwt.getExpiresAt() == null
                || !Instant.now().isBefore(storedJwt.getExpiresAt())) {
            return null;
        }

        String destination = accessor.getDestination();
        String prefix = "/topic/chat-rooms/";
        boolean ownNotifications = ("/queue/notifications-user" + accessor.getSessionId()).equals(destination);

        if (!ownNotifications && (destination == null || !destination.startsWith(prefix))) {
            return null;
        }

        String roomId = ownNotifications ? null : destination.substring(prefix.length());

        try {
            Jwt jwt = jwtDecoder.decode(storedJwt.getTokenValue());

            if (jwt.getSubject() == null) {
                return null;
            }

            Long userId = chatAccessService.requireActiveUserId(
                    UUID.fromString(jwt.getSubject())
            );

            if (!ownNotifications) chatAccessService.requireParticipant(roomId, userId);

            return message;
        } catch (JwtException | IllegalArgumentException | AppException exception) {
            return null;
        }
    }
}
