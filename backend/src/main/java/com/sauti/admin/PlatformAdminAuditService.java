package com.sauti.admin;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformAdminAuditService {
    private final PlatformAdminAuditRepository events;

    public PlatformAdminAuditService(PlatformAdminAuditRepository events) {
        this.events = events;
    }

    void record(String actorEmail, String action, String resourceType, String resourceId, String summary) {
        events.save(new PlatformAdminAuditEvent(actorEmail, action, resourceType, resourceId, summary));
    }

    @Transactional(readOnly = true)
    public AdminDtos.AuditPage list(int requestedPage, int requestedPageSize) {
        int page = Math.max(0, requestedPage);
        int size = Math.min(100, Math.max(10, requestedPageSize));
        var result = events.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));
        var items = result.getContent().stream().map(event -> new AdminDtos.AuditItem(
                event.getId(), event.getActorEmail(), event.getAction(), event.getResourceType(),
                event.getResourceId(), event.getSummary(), event.getCreatedAt())).toList();
        return new AdminDtos.AuditPage(items, result.getTotalElements(), page, size);
    }
}
