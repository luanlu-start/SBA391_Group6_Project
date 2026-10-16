package com.fptpost.modules.chat.security;

import org.springframework.context.event.EventListener;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatSocketSessions {

    private final Map<String, Jwt> sessions = new ConcurrentHashMap<>();

    public void register(String sessionId, Jwt jwt) {
        sessions.put(sessionId, jwt);
    }

    public Jwt find(String sessionId) {
        return sessionId == null ? null : sessions.get(sessionId);
    }

    public void remove(String sessionId) {
        if (sessionId != null) {
            sessions.remove(sessionId);
        }
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        remove(event.getSessionId());
    }
}