package com.fptpost.modules.notification;

import com.fptpost.modules.chat.security.*;
import com.fptpost.common.exception.AppException;
import com.fptpost.common.exception.GlobalErrorCode;
import org.junit.jupiter.api.*;
import org.springframework.messaging.*;
import org.springframework.messaging.simp.*;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotificationSocketSecurityTest {
    private final UUID owner = UUID.randomUUID();
    private final JwtDecoder decoder = mock(JwtDecoder.class);
    private final ChatAccessService access = mock(ChatAccessService.class);
    private final ChatSocketSessions sessions = new ChatSocketSessions();
    private final ChatStompInterceptor inbound = new ChatStompInterceptor(decoder, access, sessions);
    private final ChatOutboundInterceptor outbound = new ChatOutboundInterceptor(sessions, access, decoder);
    private Jwt jwt;

    @BeforeEach void setup() {
        jwt = Jwt.withTokenValue("notification-socket-test").header("alg", "HS256").subject(owner.toString())
                .issuedAt(Instant.now().minusSeconds(5)).expiresAt(Instant.now().plusSeconds(60)).build();
        when(decoder.decode(jwt.getTokenValue())).thenReturn(jwt); when(access.requireActiveUserId(owner)).thenReturn(1L);
        sessions.register("session-one", jwt);
    }

    @Test void authenticatedUserCanSubscribeOnlyToOwnNotificationUserDestination() {
        var own = inboundMessage(StompCommand.SUBSCRIBE, "/user/queue/notifications", true);
        assertThat(inbound.preSend(own, mock(MessageChannel.class))).isSameAs(own);
        verify(access).requireActiveUserId(owner); verify(access, never()).requireParticipant(anyString(), anyLong());
        for (String forbidden : new String[] {"/queue/notifications", "/topic/notifications", "/user/other/queue/notifications", "/user/queue/notifications/extra"})
            assertThatThrownBy(() -> inbound.preSend(inboundMessage(StompCommand.SUBSCRIBE, forbidden, true), mock(MessageChannel.class)))
                    .isInstanceOf(AccessDeniedException.class);
    }

    @Test void anonymousSubscriptionsAndClientSendsRemainDenied() {
        assertThatThrownBy(() -> inbound.preSend(inboundMessage(StompCommand.SUBSCRIBE, "/user/queue/notifications", false), mock(MessageChannel.class)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> inbound.preSend(inboundMessage(StompCommand.SEND, "/queue/notifications", true), mock(MessageChannel.class)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test void outboundPrivateQueueMustMatchCurrentSessionExactly() {
        var own = outboundMessage("/queue/notifications-usersession-one", "session-one");
        assertThat(outbound.preSend(own, mock(MessageChannel.class))).isSameAs(own);
        assertThat(outbound.preSend(outboundMessage("/queue/notifications-usersession-two", "session-one"), mock(MessageChannel.class))).isNull();
        assertThat(outbound.preSend(outboundMessage("/queue/notifications", "session-one"), mock(MessageChannel.class))).isNull();
        verify(access, never()).requireParticipant(anyString(), anyLong());
    }

    @Test void revokedTokenCannotReceivePrivateNotification() {
        when(decoder.decode(jwt.getTokenValue())).thenThrow(new JwtException("revoked"));
        assertThat(outbound.preSend(outboundMessage("/queue/notifications-usersession-one", "session-one"), mock(MessageChannel.class))).isNull();
    }

    @Test void lockedAccountCannotSubscribeOrReceiveQueuedNotification() {
        when(access.requireActiveUserId(owner)).thenThrow(new AppException(GlobalErrorCode.FORBIDDEN));
        assertThatThrownBy(() -> inbound.preSend(inboundMessage(StompCommand.SUBSCRIBE,
                "/user/queue/notifications", true), mock(MessageChannel.class))).isInstanceOf(AppException.class);
        assertThat(outbound.preSend(outboundMessage("/queue/notifications-usersession-one", "session-one"),
                mock(MessageChannel.class))).isNull();
    }

    @Test void chatRoomParticipantCheckIsPreservedInBothDirections() {
        String room = "0123456789abcdef01234567";
        var request = inboundMessage(StompCommand.SUBSCRIBE, "/topic/chat-rooms/" + room, true);
        assertThat(inbound.preSend(request, mock(MessageChannel.class))).isSameAs(request);
        var response = outboundMessage("/topic/chat-rooms/" + room, "session-one");
        assertThat(outbound.preSend(response, mock(MessageChannel.class))).isSameAs(response);
        verify(access, times(2)).requireParticipant(room, 1L);
    }

    private Message<byte[]> inboundMessage(StompCommand command, String destination, boolean authenticated) {
        var headers = StompHeaderAccessor.create(command); headers.setSessionId("session-one"); headers.setDestination(destination);
        if (authenticated) headers.setUser(new JwtAuthenticationToken(jwt));
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }

    private Message<byte[]> outboundMessage(String destination, String session) {
        var headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE); headers.setDestination(destination); headers.setSessionId(session);
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }
}
