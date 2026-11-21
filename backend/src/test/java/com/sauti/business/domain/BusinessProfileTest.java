package com.sauti.business.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BusinessProfileTest {
    private final OffsetDateTime now = OffsetDateTime.parse("2026-11-21T10:00:00Z");

    @Test
    void factsUseAgentVariableKeysAndSkipEmptyOnes() {
        var profile = new BusinessProfile(UUID.randomUUID(), now);
        profile.update(details(List.of(
                new ServiceOffering("Cleaning", new BigDecimal("3500"), "kes", 30, null),
                new ServiceOffering("Whitening", new BigDecimal("12000.50"), "KES", null, "includes polish, take-home kit"),
                new ServiceOffering("Consultation", null, null, 15, null)
        )), now);

        var facts = profile.facts("Bright Smile Workspace");

        assertThat(facts).containsEntry("business_name", "Bright Smile Dental")
                .containsEntry("business_address", "Ngong Road, Nairobi")
                .containsEntry("services", "Cleaning — KES 3,500 — 30 min\n"
                        + "Whitening — KES 12,000.50 — includes polish, take-home kit\n"
                        + "Consultation — 15 min")
                .containsEntry("faq", "Q: Is there parking? A: Yes, behind the building.")
                .doesNotContainKeys("business_website", "business_email", "cancellation_policy");
    }

    @Test
    void findsTheLengthOfTheServiceACallerNamedOnlyWhenItIsClear() {
        var profile = new BusinessProfile(UUID.randomUUID(), now);
        profile.update(details(List.of(
                new ServiceOffering("Check-up and cleaning", null, null, 45, null),
                new ServiceOffering("Whitening", null, null, 60, null),
                new ServiceOffering("Whitening touch-up", null, null, 20, null),
                new ServiceOffering("Consultation", null, null, null, null)
        )), now);

        assertThat(profile.durationFor("check-up and cleaning")).hasValue(45);
        assertThat(profile.durationFor("cleaning")).hasValue(45);
        assertThat(profile.durationFor("Whitening")).hasValue(60);
        assertThat(profile.durationFor("whitening touch")).hasValue(20);
        assertThat(profile.durationFor("touch")).hasValue(20);
        assertThat(profile.durationFor("consultation")).isEmpty();
        assertThat(profile.durationFor("root canal")).isEmpty();
        assertThat(profile.durationFor(" ")).isEmpty();
    }

    @Test
    void theWorkspaceNameStandsInForAnEmptyBusinessName() {
        var profile = new BusinessProfile(UUID.randomUUID(), now);
        profile.update(new BusinessProfile.Details(null, null, null, null, null, null, null, List.of(), List.of()), now);

        assertThat(profile.facts("Bright Smile Workspace")).containsExactlyEntriesOf(
                java.util.Map.of("business_name", "Bright Smile Workspace"));
    }

    @Test
    void rejectsValuesAgentsCouldNotUseSafely() {
        assertThatThrownBy(() -> new ServiceOffering("Cleaning", new BigDecimal("10"), null, null, null))
                .hasMessageContaining("3-letter currency");
        assertThatThrownBy(() -> new ServiceOffering("Cleaning", null, null, 2, null))
                .hasMessageContaining("between 5 and 480");
        assertThatThrownBy(() -> new ServiceOffering(" ", null, null, null, null)).hasMessageContaining("needs a name");
        assertThatThrownBy(() -> new FaqEntry("Parking?", " ")).hasMessageContaining("question and an answer");
        var profile = new BusinessProfile(UUID.randomUUID(), now);
        assertThatThrownBy(() -> profile.update(new BusinessProfile.Details(
                null, null, null, null, "not-an-email", null, null, List.of(), List.of()), now))
                .isInstanceOf(BusinessProfileException.class);
    }

    private static BusinessProfile.Details details(List<ServiceOffering> services) {
        return new BusinessProfile.Details("Bright Smile Dental", null, "Ngong Road, Nairobi", "+254700000000",
                null, null, null, services, List.of(new FaqEntry("Is there parking?", "Yes, behind the building.")));
    }
}
