package com.sauti.audit.application;

import com.sauti.audit.application.port.RequestOrigin;
import com.sauti.audit.domain.AuditEvent;
import com.sauti.audit.domain.AuditEventRepository;
import com.sauti.auth.application.AccountActivity;
import com.sauti.tenant.application.WorkspaceActivity;
import com.sauti.tenant.application.port.MemberDirectory;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the activity log from events published by other modules, in the same transaction as the
 * change, and reads it back for the workspace's Activity screen.
 */
@Service
public class ActivityLog {
    private final AuditEventRepository events;
    private final MemberDirectory directory;
    private final RequestOrigin origin;
    private final Clock clock;

    @Autowired
    public ActivityLog(AuditEventRepository events, MemberDirectory directory, RequestOrigin origin) {
        this(events, directory, origin, Clock.systemUTC());
    }

    ActivityLog(AuditEventRepository events, MemberDirectory directory, RequestOrigin origin, Clock clock) {
        this.events = events;
        this.directory = directory;
        this.origin = origin;
        this.clock = clock;
    }

    @EventListener
    public void onWorkspaceActivity(WorkspaceActivity activity) {
        var label = activity.actorLabel() != null ? activity.actorLabel() : emailOf(activity.actorUserId());
        events.save(AuditEvent.inWorkspace(activity.workspaceId(), activity.actorUserId(), activity.actorApiKeyId(),
                label, activity.action(), activity.targetType(), activity.targetId(), activity.summary(),
                origin.ipHash(), now()));
    }

    @EventListener
    public void onAccountActivity(AccountActivity activity) {
        events.save(AuditEvent.onAccount(activity.userId(), emailOf(activity.userId()), activity.action(),
                activity.summary(), origin.ipHash(), now()));
    }

    /** Newest first, 50 at a time. Pass the oldest time already shown to get the next page. */
    @Transactional(readOnly = true)
    public List<ActivityView> workspaceActivity(UUID workspaceId, OffsetDateTime before) {
        var cursor = before == null ? now().plusMinutes(1) : before;
        return views(events.findTop50ByWorkspaceIdAndCreatedAtBeforeOrderByCreatedAtDesc(workspaceId, cursor));
    }

    @Transactional(readOnly = true)
    public List<ActivityView> accountActivity(UUID userId) {
        return views(events.findTop20ByUserIdOrderByCreatedAtDesc(userId));
    }

    /**
     * Shows each person by the name they have chosen now; the label written with the entry (their email
     * then, "Sauti", or an API key's name) is kept for everyone else.
     */
    private List<ActivityView> views(List<AuditEvent> rows) {
        var actors = rows.stream().map(AuditEvent::actorUserId).filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        var profiles = actors.isEmpty() ? java.util.Map.<UUID, MemberDirectory.MemberProfile>of() : directory.profiles(actors);
        return rows.stream().map(event -> {
            var profile = event.actorUserId() == null ? null : profiles.get(event.actorUserId());
            var name = profile == null ? null : profile.displayName();
            return ActivityView.of(event, name == null || name.isBlank() ? event.actorLabel() : name);
        }).toList();
    }

    private String emailOf(UUID userId) {
        if (userId == null) return "Sauti";
        var profile = directory.profiles(List.of(userId)).get(userId);
        return profile == null ? "A former member" : profile.email();
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    public record ActivityView(UUID id, String action, String actor, String summary, OffsetDateTime at) {
        static ActivityView of(AuditEvent event, String actor) {
            return new ActivityView(event.id(), event.action(), actor, event.summary(), event.createdAt());
        }
    }
}
