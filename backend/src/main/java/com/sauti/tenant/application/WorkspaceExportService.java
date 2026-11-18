package com.sauti.tenant.application;

import com.sauti.tenant.TenantRepository;
import com.sauti.tenant.TenantDtos.TenantResponse;
import com.sauti.tenant.application.port.WorkspaceDataExporter;
import com.sauti.tenant.domain.MembershipPolicy;
import com.sauti.tenant.domain.MembershipRepository;
import com.sauti.tenant.domain.Permission;
import com.sauti.tenant.domain.WorkspaceNotFoundException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A copy of everything a workspace holds, for the Owner to download (data portability). Each
 * module contributes its own section through {@link WorkspaceDataExporter}.
 */
@Service
public class WorkspaceExportService {
    static final int FORMAT_VERSION = 1;

    private final TenantRepository tenants;
    private final MembershipRepository memberships;
    private final MembershipService members;
    private final List<WorkspaceDataExporter> exporters;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Autowired
    public WorkspaceExportService(TenantRepository tenants, MembershipRepository memberships, MembershipService members,
                                  List<WorkspaceDataExporter> exporters, ApplicationEventPublisher events) {
        this(tenants, memberships, members, exporters, events, Clock.systemUTC());
    }

    WorkspaceExportService(TenantRepository tenants, MembershipRepository memberships, MembershipService members,
                           List<WorkspaceDataExporter> exporters, ApplicationEventPublisher events, Clock clock) {
        this.tenants = tenants;
        this.memberships = memberships;
        this.members = members;
        this.exporters = exporters;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public Map<String, Object> export(UUID workspaceId, UUID actorUserId) {
        var actor = memberships.find(workspaceId, actorUserId).orElseThrow(WorkspaceNotFoundException::new);
        MembershipPolicy.requirePermission(actor, Permission.OWN_WORKSPACE);
        var tenant = tenants.findById(workspaceId).orElseThrow(WorkspaceNotFoundException::new);
        var export = new LinkedHashMap<String, Object>();
        export.put("format", "sauti-workspace-export");
        export.put("version", FORMAT_VERSION);
        export.put("exportedAt", OffsetDateTime.now(clock));
        export.put("workspace", TenantResponse.from(tenant));
        export.put("members", members.members(workspaceId));
        exporters.forEach(exporter -> export.put(exporter.section(), exporter.export(workspaceId)));
        events.publishEvent(WorkspaceActivity.byPerson(workspaceId, actorUserId, "workspace.exported", "workspace",
                workspaceId.toString(), "Downloaded a copy of the workspace data"));
        return export;
    }
}
