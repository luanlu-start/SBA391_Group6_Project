package com.fptpost.modules.chat.security;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ChatStompInterceptor implements ChannelInterceptor {

    private final JwtDecoder jwtDecoder;
    private final ChatAccessService chatAccessService;
    private final ChatSocketSessions chatSocketSessions;
    @Override
    public Message<?> preSend(
            Message<?> message,
            MessageChannel channel
    ) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(
                        message,
                        StompHeaderAccessor.class
                );

        if (accessor == null) {
            throw new AccessDeniedException("Invalid STOMP message");
        }

        StompCommand command = accessor.getCommand();

        if (command == StompCommand.CONNECT) {
            authenticate(accessor);
            return message;
        }

        // Cho phép đóng kết nối ngay cả khi token đã hết hạn.
        if (command == StompCommand.DISCONNECT) {
            chatSocketSessions.remove(accessor.getSessionId());
            return message;
        }

        Jwt jwt = requireValidJwt(accessor);
        Long userId = chatAccessService.requireActiveUserId(
                requirePublicId(jwt)
        );

        if (command == StompCommand.SUBSCRIBE) {
            String roomId = requireRoomDestination(
                    accessor.getDestination()
            );

            chatAccessService.requireParticipant(roomId, userId);
            return message;
        }

        if (command == StompCommand.UNSUBSCRIBE || command == null) {
            return message;
        }

        // Gửi tin qua REST. Không cho client tự phát tin vào broker.
        throw new AccessDeniedException(
                "Unsupported client STOMP command"
        );
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String authorization =
                accessor.getFirstNativeHeader("Authorization");

        if (authorization == null
                || !authorization.startsWith("Bearer ")) {
            throw new AccessDeniedException("Authentication required");
        }

        Jwt jwt = decode(authorization.substring(7));
        UUID publicId = requirePublicId(jwt);

        chatAccessService.requireActiveUserId(publicId);

        accessor.setUser(new JwtAuthenticationToken(jwt));

        String sessionId = accessor.getSessionId();

        if (sessionId == null) {
            throw new AccessDeniedException("Missing WebSocket session");
        }

        chatSocketSessions.register(sessionId, jwt);
    }

    private Jwt requireValidJwt(StompHeaderAccessor accessor) {
        if (!(accessor.getUser()
                instanceof JwtAuthenticationToken authentication)) {
            throw new AccessDeniedException("Authentication required");
        }

        return decode(authentication.getToken().getTokenValue());
    }

    private Jwt decode(String token) {
        try {
            return jwtDecoder.decode(token);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new AccessDeniedException("Invalid access token");
        }
    }

    private UUID requirePublicId(Jwt jwt) {
        if (jwt.getSubject() == null) {
            throw new AccessDeniedException("Invalid token subject");
        }

        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException exception) {
            throw new AccessDeniedException("Invalid token subject");
        }
    }

    private String requireRoomDestination(String destination) {
        String prefix = "/topic/chat-rooms/";

        if (destination == null || !destination.startsWith(prefix)) {
            throw new AccessDeniedException(
                    "Subscription destination is not allowed"
            );
        }

        String roomId = destination.substring(prefix.length());

        // ID phòng hiện được MongoDB sinh dưới dạng ObjectId 24 ký tự hex.
        if (roomId.length() != 24) {
            throw new AccessDeniedException("Invalid room destination");
        }

        for (int index = 0; index < roomId.length(); index++) {
            char character = roomId.charAt(index);

            boolean digit = character >= '0' && character <= '9';
            boolean lowerHex = character >= 'a' && character <= 'f';
            boolean upperHex = character >= 'A' && character <= 'F';

            if (!digit && !lowerHex && !upperHex) {
                throw new AccessDeniedException(
                        "Invalid room destination"
                );
            }
        }

        return roomId;
    }
}