package com.sauti.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sauti.billing.BillingReadinessService;
import com.sauti.reliability.ReliabilityDrillService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class PlatformLaunchReadinessServiceTest {
    private final PlatformLaunchReadinessRepository reviews = mock(PlatformLaunchReadinessRepository.class);
    private final PlatformAdminAuditService audit = mock(PlatformAdminAuditService.class);
    private final BillingReadinessService billing = mock(BillingReadinessService.class);
    private final ReliabilityDrillService drills = mock(ReliabilityDrillService.class);

    @Test
    void reportsAutomatedAndHumanBlockersWithoutExposingConfigurationValues() {
        var environment = new MockEnvironment()
                .withProperty("sauti.auth.public-registration-enabled", "false")
                .withProperty("sauti.auth.expose-dev-tokens", "false")
                .withProperty("sauti.cors.allowed-origins", "https://sauti.uk,https://admin.sauti.uk")
                .withProperty("sauti.websocket.allowed-origin-patterns", "https://sauti.uk")
                .withProperty("server.forward-headers-strategy", "native")
                .withProperty("sauti.admin.emails", "support@sauti.uk")
                .withProperty("sauti.email.reply-to", "support@sauti.uk");
        environment.setActiveProfiles("production");
        when(drills.recent()).thenReturn(List.of(completedDrill()));
        var billingStatus = billingReadiness("ready");
        when(billing.readiness()).thenReturn(billingStatus);
        var service = new PlatformLaunchReadinessService(reviews, audit, billing, drills, environment);

        var result = service.get();

        assertThat(result.status()).isEqualTo("review_pending");
        assertThat(result.automatedBlockingChecks()).isZero();
        assertThat(result.manualBlockingChecks()).isEqualTo(4);
        assertThat(result.automatedChecks()).allMatch(PlatformLaunchReadinessService.Check::passed);
        assertThat(result.toString()).doesNotContain("support@sauti.uk");
    }

    @Test
    void refusesGeneralAvailabilityWhileAnAutomatedGateIsBlocked() {
        var environment = new MockEnvironment();
        environment.setActiveProfiles("test");
        when(drills.recent()).thenReturn(List.of());
        var billingStatus = billingReadiness("in_progress");
        when(billing.readiness()).thenReturn(billingStatus);
        when(reviews.findByIdForUpdate(PlatformLaunchReadiness.ID)).thenReturn(Optional.empty());
        var service = new PlatformLaunchReadinessService(reviews, audit, billing, drills, environment);
        var command = new PlatformLaunchReadinessService.UpdateReview(true, true, true, true,
                true, PlatformLaunchReadinessService.APPROVAL_CONFIRMATION, "Reviewed");

        assertThatThrownBy(() -> service.update(command, "admin@sauti.uk"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be approved");
        verify(reviews, never()).save(any());
    }

    @Test
    void approvesOnlyWithExactConfirmationAndCompleteEvidence() {
        var environment = new MockEnvironment()
                .withProperty("sauti.auth.public-registration-enabled", "false")
                .withProperty("sauti.auth.expose-dev-tokens", "false")
                .withProperty("sauti.cors.allowed-origins", "https://sauti.uk,https://admin.sauti.uk")
                .withProperty("sauti.websocket.allowed-origin-patterns", "https://sauti.uk")
                .withProperty("server.forward-headers-strategy", "native")
                .withProperty("sauti.admin.emails", "support@sauti.uk")
                .withProperty("sauti.email.reply-to", "support@sauti.uk");
        environment.setActiveProfiles("production");
        when(drills.recent()).thenReturn(List.of(completedDrill()));
        var billingStatus = billingReadiness("ready");
        when(billing.readiness()).thenReturn(billingStatus);
        when(reviews.findByIdForUpdate(PlatformLaunchReadiness.ID)).thenReturn(Optional.empty());
        when(reviews.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var service = new PlatformLaunchReadinessService(reviews, audit, billing, drills, environment);

        var result = service.update(new PlatformLaunchReadinessService.UpdateReview(
                true, true, true, true, true,
                PlatformLaunchReadinessService.APPROVAL_CONFIRMATION, "All evidence retained"),
                "admin@sauti.uk");

        assertThat(result.status()).isEqualTo("approved");
        assertThat(result.manualReview().generalAvailabilityApproved()).isTrue();
        verify(audit).record("admin@sauti.uk", "platform.launch.approved", "platform_launch",
                PlatformLaunchReadiness.ID, "General availability approved after all launch gates passed");
    }

    private static ReliabilityDrillService.DrillView completedDrill() {
        var now = OffsetDateTime.parse("2026-08-12T08:00:00Z");
        return new ReliabilityDrillService.DrillView(UUID.randomUUID(), "resolved", "admin", now,
                "admin", now, "admin", now, now, now);
    }

    private static BillingReadinessService.Readiness billingReadiness(String status) {
        var readiness = mock(BillingReadinessService.Readiness.class);
        when(readiness.status()).thenReturn(status);
        return readiness;
    }
}
