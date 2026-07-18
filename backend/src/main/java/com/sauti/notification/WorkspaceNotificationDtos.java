package com.sauti.notification;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class WorkspaceNotificationDtos {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private WorkspaceNotificationDtos() {
    }

    public record NotificationResponse(
            UUID id,
            String type,
            String title,
            String message,
            String href,
            String resourceType,
            UUID resourceId,
            Map<String, Object> payload,
            OffsetDateTime createdAt,
            OffsetDateTime readAt
    ) {
        public static NotificationResponse from(WorkspaceNotification notification) {
            return new NotificationResponse(
                    notification.getId(), notification.getType(), notification.getTitle(), notification.getMessage(),
                    notification.getHref(), notification.getResourceType(), notification.getResourceId(),
                    WorkspaceNotificationDtos.payload(notification.getPayload()), notification.getCreatedAt(), notification.getReadAt()
            );
        }
    }

    public record NotificationListResponse(List<NotificationResponse> notifications, long unreadCount) { }

    private static Map<String, Object> payload(String value) {
        try {
            return OBJECT_MAPPER.readValue(value, new TypeReference<>() { });
        } catch (Exception ignored) {
            return Map.of();
        }
    }
}
