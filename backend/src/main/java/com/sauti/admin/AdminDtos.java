package com.sauti.admin;

import com.sauti.demo.DemoRequest;
import java.time.OffsetDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class AdminDtos {
    private AdminDtos() { }

    public record Overview(long workspaces, long calls, long bookings, long customers,
                           long newDemoRequests, long invitedDemoRequests, long activatedPilots) { }

    public record InvitationState(UUID id, String deliveryStatus, int deliveryAttempts,
                                  OffsetDateTime lastDeliveryAttemptAt, OffsetDateTime sentAt,
                                  String lastDeliveryError, OffsetDateTime expiresAt,
                                  OffsetDateTime revokedAt, OffsetDateTime acceptedAt) { }

    public record DemoRequestItem(UUID id, String businessName, String contactName, String email,
                                  String countryCode, String phone, String industry,
                                  String monthlyCallVolume, String channels, String primaryUseCase,
                                  String notes, String status, String assignedTo, String internalNotes,
                                  OffsetDateTime rejectedAt, String rejectedReason,
                                  InvitationState invitation, OffsetDateTime createdAt) {
        static DemoRequestItem from(DemoRequest request, InvitationState invitation) {
            return new DemoRequestItem(request.getId(), request.getBusinessName(), request.getContactName(),
                    request.getEmail(), request.getCountryCode(), request.getPhone(), request.getIndustry(),
                    request.getMonthlyCallVolume(), request.getChannels(), request.getPrimaryUseCase(),
                    request.getNotes(), request.getStatus(), request.getAssignedTo(), request.getInternalNotes(),
                    request.getRejectedAt(), request.getRejectedReason(), invitation, request.getCreatedAt());
        }
    }

    public record DemoRequestPage(List<DemoRequestItem> requests, long total, int page, int pageSize) { }
    public record UpdateDemoRequest(String assignedTo, String internalNotes) { }
    public record RejectDemoRequest(String reason) { }
    public record AuditItem(UUID id, String actorEmail, String action, String resourceType,
                            String resourceId, String summary, OffsetDateTime createdAt) { }
    public record AuditPage(List<AuditItem> events, long total, int page, int pageSize) { }

    public record PilotPolicyItem(String status, String currency, BigDecimal monthlyBudget,
                                  boolean phoneNumbersApproved, boolean liveCallingApproved,
                                  boolean smsApproved, boolean whatsappApproved,
                                  String approvedBy, OffsetDateTime approvedAt, String notes) { }
    public record ConfigurePilotPolicy(String status, String currency, BigDecimal monthlyBudget,
                                       boolean phoneNumbersApproved, boolean liveCallingApproved,
                                       boolean smsApproved, boolean whatsappApproved, String notes) { }

    public record WorkspaceItem(UUID id, String businessName, String email, String countryCode,
                                String plan, String status, int minutesUsed, int minutesLimit,
                                long agents, long calls, long bookings, long customers,
                                PilotPolicyItem pilotPolicy, OffsetDateTime createdAt) { }

    public record WorkspacePage(List<WorkspaceItem> workspaces, long total, int page, int pageSize) { }

    public record CustomerItem(UUID tenantId, String businessName, String phone, long calls,
                               OffsetDateTime lastContactAt) { }

    public record CustomerPage(List<CustomerItem> customers, long total, int page, int pageSize) { }

    public record CustomerCallItem(UUID id, String agentName, String direction, String outcome,
                                   String language, Integer durationSeconds, OffsetDateTime startedAt) { }

    public record CustomerDetail(UUID tenantId, String businessName, String phone, long calls,
                                 OffsetDateTime lastContactAt, List<CustomerCallItem> recentCalls) { }

    public record PlatformAnalytics(int days, OffsetDateTime from, OffsetDateTime to,
                                    List<DailyActivity> activity, List<CostTotal> costTotals,
                                    List<DailyCost> dailyCosts, List<UnpricedUsage> unpricedUsage,
                                    List<ProviderHealth> providers, OffsetDateTime generatedAt) { }

    public record DailyActivity(String date, long calls, long completed, long failed,
                                long durationSeconds, long activeWorkspaces) { }
    public record CostTotal(String currency, String costBasis, String category, BigDecimal amount) { }
    public record DailyCost(String date, String currency, BigDecimal amount) { }
    public record UnpricedUsage(String category, String unit, BigDecimal quantity) { }
    public record ProviderHealth(String provider, String status, long configuredConnections,
                                 long connectionErrors, long deliveryAttempts, long delivered,
                                 long retryingDeliveries, long failedDeliveries,
                                 long pendingCosts, long retryingCosts, long reconciledCosts,
                                 long estimatedCosts, long unavailableCosts,
                                 OffsetDateTime lastActivityAt) { }
}
