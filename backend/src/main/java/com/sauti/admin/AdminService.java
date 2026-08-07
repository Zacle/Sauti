package com.sauti.admin;

import com.sauti.admin.AdminDtos.DemoRequestItem;
import com.sauti.admin.AdminDtos.DemoRequestPage;
import com.sauti.admin.AdminDtos.Overview;
import com.sauti.admin.AdminDtos.CustomerCallItem;
import com.sauti.admin.AdminDtos.CustomerDetail;
import com.sauti.admin.AdminDtos.CustomerItem;
import com.sauti.admin.AdminDtos.CustomerPage;
import com.sauti.admin.AdminDtos.WorkspaceItem;
import com.sauti.admin.AdminDtos.WorkspacePage;
import com.sauti.admin.AdminDtos.PlatformAnalytics;
import com.sauti.admin.AdminDtos.DailyActivity;
import com.sauti.admin.AdminDtos.CostTotal;
import com.sauti.admin.AdminDtos.DailyCost;
import com.sauti.admin.AdminDtos.UnpricedUsage;
import com.sauti.admin.AdminDtos.ProviderHealth;
import com.sauti.agent.AgentRepository;
import com.sauti.billing.PlatformCostInsightsService;
import com.sauti.calendar.BookingRepository;
import com.sauti.call.CallRepository;
import com.sauti.demo.DemoRequestRepository;
import com.sauti.demo.PilotInvitationDtos.InvitationIssued;
import com.sauti.demo.PilotInvitationService;
import com.sauti.demo.PilotInvitationRepository;
import com.sauti.integration.PlatformIntegrationHealthService;
import com.sauti.tenant.TenantRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.sauti.provisioning.PilotProvisioningPolicyService;
import com.sauti.provisioning.PilotReadinessService;
import org.springframework.context.ApplicationEventPublisher;
import com.sauti.demo.DemoRequestRejected;

@Service
public class AdminService {
    private final TenantRepository tenants;
    private final CallRepository calls;
    private final BookingRepository bookings;
    private final AgentRepository agents;
    private final DemoRequestRepository demoRequests;
    private final PilotInvitationService invitations;
    private final PlatformCostInsightsService costInsights;
    private final PlatformIntegrationHealthService integrationHealth;
    private final PilotInvitationRepository invitationRepository;
    private final PlatformAdminAuditService audit;
    private final PilotProvisioningPolicyService provisioningPolicies;
    private final ApplicationEventPublisher events;
    private final PilotReadinessService pilotReadiness;

    public AdminService(TenantRepository tenants, CallRepository calls, BookingRepository bookings,
                        AgentRepository agents,
                        DemoRequestRepository demoRequests, PilotInvitationService invitations,
                        PlatformCostInsightsService costInsights,
                        PlatformIntegrationHealthService integrationHealth,
                        PilotInvitationRepository invitationRepository,
                        PlatformAdminAuditService audit,
                        PilotProvisioningPolicyService provisioningPolicies,
                        ApplicationEventPublisher events,
                        PilotReadinessService pilotReadiness) {
        this.tenants = tenants;
        this.calls = calls;
        this.bookings = bookings;
        this.agents = agents;
        this.demoRequests = demoRequests;
        this.invitations = invitations;
        this.costInsights = costInsights;
        this.integrationHealth = integrationHealth;
        this.invitationRepository = invitationRepository;
        this.audit = audit;
        this.provisioningPolicies = provisioningPolicies;
        this.events = events;
        this.pilotReadiness = pilotReadiness;
    }

    @Transactional(readOnly = true)
    public Overview overview() {
        return new Overview(tenants.count(), calls.count(), bookings.count(), calls.countDistinctCustomerNumbers(),
                demoRequests.countByStatus("new"), demoRequests.countByStatus("invited"),
                demoRequests.countByStatus("activated"));
    }

    @Transactional(readOnly = true)
    public DemoRequestPage demoRequests(int requestedPage, int requestedPageSize) {
        int page = Math.max(0, requestedPage);
        int size = Math.min(100, Math.max(10, requestedPageSize));
        var result = demoRequests.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));
        var ids = result.getContent().stream().map(com.sauti.demo.DemoRequest::getId).toList();
        var invitationByRequest = invitationRepository.findAllByDemoRequestIdIn(ids).stream()
                .collect(java.util.stream.Collectors.toMap(com.sauti.demo.PilotInvitation::getDemoRequestId,
                        invitation -> new AdminDtos.InvitationState(invitation.getId(), invitation.getDeliveryStatus(),
                                invitation.getDeliveryAttempts(), invitation.getLastDeliveryAttemptAt(),
                                invitation.getSentAt(), invitation.getLastDeliveryError(), invitation.getExpiresAt(),
                                invitation.getRevokedAt(), invitation.getAcceptedAt())));
        return new DemoRequestPage(result.getContent().stream()
                .map(request -> DemoRequestItem.from(request, invitationByRequest.get(request.getId()))).toList(),
                result.getTotalElements(), page, size);
    }

    @Transactional
    public InvitationIssued invite(UUID requestId, String actor) {
        var issued = invitations.issue(requestId);
        audit.record(actor, "demo.invitation.issued", "demo_request", requestId.toString(),
                "Pilot invitation issued");
        return issued;
    }

    @Transactional
    public InvitationIssued resendInvitation(UUID requestId, String actor) {
        var issued = invitations.resend(requestId);
        audit.record(actor, "demo.invitation.resent", "demo_request", requestId.toString(),
                "Invitation token rotated and email resent");
        return issued;
    }

    @Transactional
    public void revokeInvitation(UUID requestId, String actor) {
        invitations.revoke(requestId);
        audit.record(actor, "demo.invitation.revoked", "demo_request", requestId.toString(),
                "Pilot invitation revoked");
    }

    @Transactional
    public DemoRequestItem reject(UUID requestId, String reason, String actor) {
        var request = demoRequests.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Demo request not found"));
        invitations.revokeForRejection(requestId);
        request.reject(reason, OffsetDateTime.now(ZoneOffset.UTC));
        events.publishEvent(new DemoRequestRejected(request));
        audit.record(actor, "demo.request.rejected", "demo_request", requestId.toString(),
                "Demo request rejected: " + safeSummary(reason));
        return DemoRequestItem.from(request, invitationState(requestId));
    }

    @Transactional
    public DemoRequestItem updateDemoOperations(UUID requestId, String assignedTo, String internalNotes,
                                                 String actor) {
        var request = demoRequests.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Demo request not found"));
        request.updateOperations(assignedTo, internalNotes);
        audit.record(actor, "demo.request.operations_updated", "demo_request", requestId.toString(),
                "Assignment and internal notes updated");
        return DemoRequestItem.from(request, invitationState(requestId));
    }

    @Transactional(readOnly = true)
    public WorkspacePage workspaces(String requestedQuery, int requestedPage, int requestedPageSize) {
        var page = page(requestedPage);
        var size = pageSize(requestedPageSize);
        var result = tenants.searchForPlatformAdmin(query(requestedQuery), PageRequest.of(page, size));
        var items = result.getContent().stream().map(tenant -> new WorkspaceItem(
                tenant.getId(), tenant.getBusinessName(), tenant.getEmail(), tenant.getCountryCode(),
                tenant.getPlan(), tenant.getStatus(), tenant.getMinutesUsedThisCycle(), tenant.getMonthlyMinutesLimit(),
                agents.countByTenantId(tenant.getId()), calls.countByTenantId(tenant.getId()),
                bookings.countByTenantId(tenant.getId()), calls.countDistinctCustomerNumbersByTenantId(tenant.getId()),
                pilotPolicy(tenant.getId()), tenant.getCreatedAt()
        )).toList();
        return new WorkspacePage(items, result.getTotalElements(), page, size);
    }

    @Transactional(readOnly = true)
    public WorkspaceItem workspace(UUID tenantId) {
        var tenant = tenants.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found"));
        return new WorkspaceItem(tenant.getId(), tenant.getBusinessName(), tenant.getEmail(),
                tenant.getCountryCode(), tenant.getPlan(), tenant.getStatus(), tenant.getMinutesUsedThisCycle(),
                tenant.getMonthlyMinutesLimit(), agents.countByTenantId(tenantId), calls.countByTenantId(tenantId),
                bookings.countByTenantId(tenantId), calls.countDistinctCustomerNumbersByTenantId(tenantId),
                pilotPolicy(tenantId), tenant.getCreatedAt());
    }

    @Transactional
    public WorkspaceItem configurePilotPolicy(UUID tenantId, AdminDtos.ConfigurePilotPolicy request, String actor) {
        tenants.findById(tenantId).orElseThrow(() -> new IllegalArgumentException("Workspace not found"));
        var policy = provisioningPolicies.configure(tenantId, request.status(), request.currency(),
                request.monthlyBudget(), request.phoneNumbersApproved(), request.liveCallingApproved(),
                request.smsApproved(), request.whatsappApproved(), request.notes(), actor);
        audit.record(actor, "pilot.provisioning_policy.updated", "workspace", tenantId.toString(),
                "Pilot budget and provider capabilities updated to " + policy.getStatus());
        return workspace(tenantId);
    }

    @Transactional(readOnly = true)
    public AdminDtos.PilotReadinessItem pilotReadiness(UUID tenantId) {
        return pilotReadiness.get(tenantId);
    }

    @Transactional
    public AdminDtos.PilotReadinessItem updatePilotReadiness(UUID tenantId,
                                                              AdminDtos.UpdatePilotReadiness request,
                                                              String actor) {
        var result = pilotReadiness.update(tenantId, request, actor);
        audit.record(actor, "pilot.readiness.updated", "workspace", tenantId.toString(),
                result.readyForLaunch() ? "Pilot launch readiness approved" : "Pilot readiness review updated");
        return result;
    }

    @Transactional(readOnly = true)
    public CustomerPage customers(String requestedQuery, int requestedPage, int requestedPageSize) {
        var normalized = query(requestedQuery).toLowerCase(Locale.ROOT);
        var matching = calls.findPlatformCustomerSummaries().stream()
                .filter(row -> normalized.isBlank()
                        || row.getPhone().toLowerCase(Locale.ROOT).contains(normalized)
                        || row.getBusinessName().toLowerCase(Locale.ROOT).contains(normalized))
                .toList();
        var page = page(requestedPage);
        var size = pageSize(requestedPageSize);
        var from = (int) Math.min((long) page * size, matching.size());
        var to = Math.min(from + size, matching.size());
        var items = matching.subList(from, to).stream().map(row -> new CustomerItem(
                row.getTenantId(), row.getBusinessName(), row.getPhone(), row.getCallCount(), row.getLastContactAt()
        )).toList();
        return new CustomerPage(items, matching.size(), page, size);
    }

    @Transactional(readOnly = true)
    public CustomerDetail customer(UUID tenantId, String phone) {
        var normalizedPhone = phone == null ? "" : phone.trim();
        if (normalizedPhone.isBlank()) throw new IllegalArgumentException("Customer phone is required");
        var tenant = tenants.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found"));
        var recent = calls.findTop25ByTenantIdAndCallerNumberOrderByStartedAtDesc(tenantId, normalizedPhone);
        if (recent.isEmpty()) throw new IllegalArgumentException("Customer not found");
        var callItems = recent.stream().map(call -> new CustomerCallItem(
                call.getId(), call.getAgent().getName(), call.getDirection(), call.getOutcome(),
                call.getLanguageDetected(), call.getDurationSeconds(), call.getStartedAt()
        )).toList();
        return new CustomerDetail(tenantId, tenant.getBusinessName(), normalizedPhone,
                calls.countByTenantIdAndCallerNumber(tenantId, normalizedPhone), recent.get(0).getStartedAt(), callItems);
    }

    @Transactional(readOnly = true)
    public PlatformAnalytics analytics(int requestedDays) {
        var days = requestedDays == 7 || requestedDays == 90 ? requestedDays : 30;
        var today = LocalDate.now(ZoneOffset.UTC);
        var from = today.minusDays(days - 1L).atStartOfDay().atOffset(ZoneOffset.UTC);
        var to = today.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
        var activity = dailyActivity(from, to);
        var cost = costInsights.snapshot(from);
        var providers = providerHealth(integrationHealth.snapshot(from), cost.reconciliation());
        return new PlatformAnalytics(days, from, to, activity,
                cost.costTotals().stream().map(item -> new CostTotal(
                        item.currency(), item.costBasis(), item.category(), item.amount())).toList(),
                cost.dailyCosts().stream().map(item -> new DailyCost(
                        item.date(), item.currency(), item.amount())).toList(),
                cost.unpricedUsage().stream().map(item -> new UnpricedUsage(
                        item.category(), item.unit(), item.quantity())).toList(),
                providers, OffsetDateTime.now(ZoneOffset.UTC));
    }

    private java.util.List<DailyActivity> dailyActivity(OffsetDateTime from, OffsetDateTime to) {
        var callsByDay = calls.findAllByStartedAtBetweenOrderByStartedAtAsc(from, to).stream()
                .collect(java.util.stream.Collectors.groupingBy(call ->
                        call.getStartedAt().withOffsetSameInstant(ZoneOffset.UTC).toLocalDate()));
        var result = new ArrayList<DailyActivity>();
        for (var date = from.toLocalDate(); date.isBefore(to.toLocalDate()); date = date.plusDays(1)) {
            var daily = callsByDay.getOrDefault(date, java.util.List.of());
            var failed = daily.stream().filter(call -> java.util.Set.of(
                    "failed", "busy", "no_answer", "canceled").contains(call.getOutcome())).count();
            var completed = daily.stream().filter(call -> call.getEndedAt() != null && !java.util.Set.of(
                    "failed", "busy", "no_answer", "canceled", "active").contains(call.getOutcome())).count();
            var duration = daily.stream().map(call -> call.getDurationSeconds() == null ? 0 : call.getDurationSeconds())
                    .mapToLong(Integer::longValue).sum();
            var activeWorkspaces = daily.stream().map(call -> call.getTenant().getId()).distinct().count();
            result.add(new DailyActivity(date.toString(), daily.size(), completed, failed, duration, activeWorkspaces));
        }
        return result;
    }

    private java.util.List<ProviderHealth> providerHealth(
            java.util.List<PlatformIntegrationHealthService.ProviderHealth> integrations,
            java.util.List<PlatformCostInsightsService.ReconciliationHealth> reconciliations) {
        Map<String, ProviderAccumulator> providers = new LinkedHashMap<>();
        integrations.forEach(item -> providers.computeIfAbsent(item.provider(), ProviderAccumulator::new)
                .integration(item));
        reconciliations.forEach(item -> providers.computeIfAbsent(item.provider(), ProviderAccumulator::new)
                .reconciliation(item));
        return providers.values().stream().map(ProviderAccumulator::response)
                .sorted(Comparator.comparing(ProviderHealth::provider)).toList();
    }

    private static final class ProviderAccumulator {
        private final String provider;
        private String status = "unknown";
        private long configuredConnections;
        private long connectionErrors;
        private long deliveryAttempts;
        private long delivered;
        private long retryingDeliveries;
        private long failedDeliveries;
        private long pendingCosts;
        private long retryingCosts;
        private long reconciledCosts;
        private long estimatedCosts;
        private long unavailableCosts;
        private OffsetDateTime lastActivityAt;

        private ProviderAccumulator(String provider) { this.provider = provider; }
        private ProviderAccumulator integration(PlatformIntegrationHealthService.ProviderHealth item) {
            status = worse(status, item.status()); configuredConnections = item.configuredConnections();
            connectionErrors = item.connectionErrors(); deliveryAttempts = item.deliveryAttempts();
            delivered = item.delivered(); retryingDeliveries = item.retrying(); failedDeliveries = item.failed();
            lastActivityAt = item.lastActivityAt(); return this;
        }
        private ProviderAccumulator reconciliation(PlatformCostInsightsService.ReconciliationHealth item) {
            pendingCosts = item.pending(); retryingCosts = item.retrying(); reconciledCosts = item.reconciled();
            estimatedCosts = item.estimated(); unavailableCosts = item.unavailable();
            var costStatus = unavailableCosts > 0 ? "attention"
                    : pendingCosts + retryingCosts > 0 ? "degraded"
                    : reconciledCosts + estimatedCosts > 0 ? "healthy" : "unknown";
            status = worse(status, costStatus); return this;
        }
        private ProviderHealth response() {
            return new ProviderHealth(provider, status, configuredConnections, connectionErrors,
                    deliveryAttempts, delivered, retryingDeliveries, failedDeliveries, pendingCosts,
                    retryingCosts, reconciledCosts, estimatedCosts, unavailableCosts, lastActivityAt);
        }
        private static String worse(String left, String right) {
            var order = java.util.List.of("unknown", "healthy", "degraded", "attention");
            return order.indexOf(left) >= order.indexOf(right) ? left : right;
        }
    }

    private int page(int requested) {
        return Math.max(0, requested);
    }

    private int pageSize(int requested) {
        return Math.min(100, Math.max(10, requested));
    }

    private String query(String requested) {
        return requested == null ? "" : requested.trim();
    }

    private AdminDtos.InvitationState invitationState(UUID requestId) {
        return invitationRepository.findByDemoRequestId(requestId).map(invitation ->
                new AdminDtos.InvitationState(invitation.getId(), invitation.getDeliveryStatus(),
                        invitation.getDeliveryAttempts(), invitation.getLastDeliveryAttemptAt(),
                        invitation.getSentAt(), invitation.getLastDeliveryError(), invitation.getExpiresAt(),
                        invitation.getRevokedAt(), invitation.getAcceptedAt())).orElse(null);
    }

    private String safeSummary(String value) {
        var normalized = value == null ? "" : value.trim().replaceAll("[\\r\\n]+", " ");
        return normalized.substring(0, Math.min(300, normalized.length()));
    }

    private AdminDtos.PilotPolicyItem pilotPolicy(UUID tenantId) {
        var policy = provisioningPolicies.find(tenantId);
        return policy == null ? null : new AdminDtos.PilotPolicyItem(policy.getStatus(), policy.getCurrency(),
                policy.getMonthlyBudget(), policy.isPhoneNumbersApproved(), policy.isLiveCallingApproved(),
                policy.isSmsApproved(), policy.isWhatsappApproved(), policy.getApprovedBy(),
                policy.getApprovedAt(), policy.getNotes());
    }
}
