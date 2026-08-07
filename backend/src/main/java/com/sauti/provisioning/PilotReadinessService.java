package com.sauti.provisioning;

import com.sauti.admin.AdminDtos.PilotReadinessCheck;
import com.sauti.admin.AdminDtos.PilotReadinessItem;
import com.sauti.admin.AdminDtos.UpdatePilotReadiness;
import com.sauti.agent.AgentReadinessService;
import com.sauti.agent.AgentRepository;
import com.sauti.call.CallRepository;
import com.sauti.tenant.TenantRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PilotReadinessService {
    private final TenantRepository tenants;
    private final AgentRepository agents;
    private final AgentReadinessService agentReadiness;
    private final CallRepository calls;
    private final PilotProvisioningPolicyService policies;
    private final PilotReadinessReviewRepository reviews;

    public PilotReadinessService(TenantRepository tenants, AgentRepository agents,
                                 AgentReadinessService agentReadiness, CallRepository calls,
                                 PilotProvisioningPolicyService policies,
                                 PilotReadinessReviewRepository reviews) {
        this.tenants = tenants;
        this.agents = agents;
        this.agentReadiness = agentReadiness;
        this.calls = calls;
        this.policies = policies;
        this.reviews = reviews;
    }

    @Transactional(readOnly = true)
    public PilotReadinessItem get(UUID tenantId) {
        tenants.findById(tenantId).orElseThrow(() -> new IllegalArgumentException("Workspace not found"));
        return snapshot(tenantId, reviews.findByTenantId(tenantId).orElse(null));
    }

    @Transactional
    public PilotReadinessItem update(UUID tenantId, UpdatePilotReadiness request, String actor) {
        tenants.findById(tenantId).orElseThrow(() -> new IllegalArgumentException("Workspace not found"));
        validateSupport(request);
        var review = reviews.findByTenantId(tenantId).orElseGet(() -> new PilotReadinessReview(tenantId));
        review.update(request.supportContactName(), request.supportContactEmail(), request.supportContactPhone(),
                request.launchNotes(), false, actor, OffsetDateTime.now(ZoneOffset.UTC));
        reviews.save(review);
        var draft = snapshot(tenantId, review);
        if (request.launchApproved() && draft.blockingChecks() > 0) {
            throw new IllegalStateException("Resolve every required pilot-readiness check before approving launch");
        }
        review.update(request.supportContactName(), request.supportContactEmail(), request.supportContactPhone(),
                request.launchNotes(), request.launchApproved(), actor, OffsetDateTime.now(ZoneOffset.UTC));
        return snapshot(tenantId, review);
    }

    private PilotReadinessItem snapshot(UUID tenantId, PilotReadinessReview review) {
        var workspaceAgents = agents.findAllByTenantIdOrderByCreatedAtDesc(tenantId);
        var readiness = agentReadiness.list(tenantId);
        var policy = policies.find(tenantId);
        boolean activeReadyAgent = readiness.stream().anyMatch(item -> item.active() && item.readyToActivate());
        boolean phoneRequired = policy != null && (policy.isPhoneNumbersApproved() || policy.isLiveCallingApproved());
        boolean phoneReady = readiness.stream().anyMatch(item -> item.active() && item.phoneNumberConfigured());
        boolean calendarConnected = workspaceAgents.stream().anyMatch(agent -> agent.getCalendarProvider() != null
                && !agent.getCalendarProvider().isBlank()
                && !"Set up later".equalsIgnoreCase(agent.getCalendarProvider()));
        boolean smsRequired = policy != null && policy.isSmsApproved();
        boolean whatsappRequired = policy != null && policy.isWhatsappApproved();
        boolean messagingRequired = smsRequired || whatsappRequired;
        boolean messagingReady = (!smsRequired || phoneReady)
                && (!whatsappRequired || readiness.stream().anyMatch(item -> item.active() && item.whatsappConfigured()));
        boolean testCallReady = calls.existsByTenantIdAndDirectionAndEndedAtIsNotNull(tenantId, "test");
        boolean supportReady = hasSupport(review);

        var checks = new ArrayList<PilotReadinessCheck>();
        checks.add(check("agent_setup", "Agent setup", true, activeReadyAgent,
                activeReadyAgent ? "An active agent has complete business details and a usable channel."
                        : "Activate at least one agent with complete business details and a usable channel."));
        checks.add(check("phone_ownership", "Phone ownership", phoneRequired, phoneReady,
                phoneRequired ? (phoneReady ? "An active owned number is assigned." : "Assign an active owned number to the pilot agent.")
                        : "Not required until phone numbers or live calling are approved."));
        checks.add(new PilotReadinessCheck("calendar_sync", "Calendar sync",
                calendarConnected ? "ready" : "not_required", false,
                calendarConnected ? "An external calendar is configured; Sauti remains the source of truth."
                        : "Optional. Sauti bookings work without an external calendar."));
        checks.add(check("messaging", "Messaging", messagingRequired, messagingReady,
                messagingRequired ? (messagingReady ? "Every approved messaging channel is configured."
                        : "Configure every messaging channel enabled in the pilot policy.")
                        : "Not required until SMS or WhatsApp is approved."));
        checks.add(check("test_call", "Completed test call", true, testCallReady,
                testCallReady ? "A browser test call completed successfully."
                        : "Complete at least one browser test call before launch."));
        checks.add(check("support_contact", "Support contact", true, supportReady,
                supportReady ? "A named escalation contact is available."
                        : "Add a support contact name and an email address or phone number."));
        long blocking = checks.stream().filter(item -> item.required() && !"ready".equals(item.status())).count();
        boolean approved = review != null && review.isLaunchApproved();
        return new PilotReadinessItem(checks, checks.stream().filter(item -> "ready".equals(item.status())).count(),
                blocking, approved, approved && blocking == 0,
                review == null ? null : review.getSupportContactName(),
                review == null ? null : review.getSupportContactEmail(),
                review == null ? null : review.getSupportContactPhone(),
                review == null ? null : review.getLaunchNotes(),
                review == null ? null : review.getApprovedBy(),
                review == null ? null : review.getApprovedAt());
    }

    private PilotReadinessCheck check(String key, String label, boolean required, boolean ready, String detail) {
        return new PilotReadinessCheck(key, label, !required ? "not_required" : ready ? "ready" : "not_ready",
                required, detail);
    }

    private boolean hasSupport(PilotReadinessReview review) {
        return review != null && present(review.getSupportContactName())
                && (present(review.getSupportContactEmail()) || present(review.getSupportContactPhone()));
    }

    private void validateSupport(UpdatePilotReadiness request) {
        if (present(request.supportContactEmail()) && !request.supportContactEmail().trim().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("Support contact email is invalid");
        }
    }

    private boolean present(String value) { return value != null && !value.isBlank(); }
}

