package com.sauti.dashboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.auth.JwtService;
import com.sauti.dashboard.DashboardDtos.DashboardEvent;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

@Component
public class DashboardWebSocketHandler extends AbstractWebSocketHandler {
    private static final String TENANT_ID_ATTRIBUTE = "dashboardTenantId";

    private final ObjectMapper objectMapper;
    private final JwtService jwtService;
    private final Map<UUID, Set<WebSocketSession>> sessionsByTenant = new ConcurrentHashMap<>();

    public DashboardWebSocketHandler(ObjectMapper objectMapper, JwtService jwtService) {
        this.objectMapper = objectMapper;
        this.jwtService = jwtService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        var tenantId = tenantId(session);
        var token = queryParams(session).getOrDefault("token", "");
        if (token.isBlank()) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("missing token"));
            return;
        }
        try {
            var user = jwtService.authenticate(token);
            if (!tenantId.equals(user.tenantId())) {
                session.close(CloseStatus.POLICY_VIOLATION.withReason("tenant mismatch"));
                return;
            }
        } catch (RuntimeException exception) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("invalid token"));
            return;
        }
        session.getAttributes().put(TENANT_ID_ATTRIBUTE, tenantId);
        sessionsByTenant.computeIfAbsent(tenantId, ignored -> ConcurrentHashMap.newKeySet()).add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        var tenantId = session.getAttributes().get(TENANT_ID_ATTRIBUTE);
        if (tenantId instanceof UUID uuid) {
            var sessions = sessionsByTenant.get(uuid);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    sessionsByTenant.remove(uuid, sessions);
                }
            }
        }
    }

    public void publish(DashboardEvent event) {
        var sessions = sessionsByTenant.get(event.tenantId());
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (Exception exception) {
            return;
        }
        for (var session : Set.copyOf(sessions)) {
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(payload));
                }
            } catch (Exception exception) {
                try {
                    session.close(CloseStatus.SERVER_ERROR);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private UUID tenantId(WebSocketSession session) {
        var path = session.getUri() == null ? "" : session.getUri().getPath();
        return UUID.fromString(path.substring(path.lastIndexOf('/') + 1));
    }

    private Map<String, String> queryParams(WebSocketSession session) {
        var query = session.getUri() == null ? "" : session.getUri().getRawQuery();
        if (query == null || query.isBlank()) {
            return Map.of();
        }
        var params = new ConcurrentHashMap<String, String>();
        for (var pair : query.split("&")) {
            var parts = pair.split("=", 2);
            if (parts.length == 2) {
                params.put(
                        URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(parts[1], StandardCharsets.UTF_8)
                );
            }
        }
        return params;
    }
}
