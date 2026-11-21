package com.sauti.business.infrastructure;

import com.sauti.agent.BusinessFacts;
import com.sauti.business.application.BusinessProfileService;
import com.sauti.business.application.port.PhoneNumberFormat;
import com.sauti.business.application.port.WorkspaceLookup;
import com.sauti.phone.InternationalPhoneNumberService;
import com.sauti.tenant.TenantRepository;
import com.sauti.tenant.domain.WorkspaceNotFoundException;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Connects the business profile to the rest of Sauti. */
final class BusinessProfileAdapters {
    private BusinessProfileAdapters() {
    }

    @Component
    static class TenantWorkspaceLookup implements WorkspaceLookup {
        private final TenantRepository tenants;

        TenantWorkspaceLookup(TenantRepository tenants) {
            this.tenants = tenants;
        }

        @Override
        public Workspace require(UUID workspaceId) {
            var tenant = tenants.findById(workspaceId).orElseThrow(WorkspaceNotFoundException::new);
            return new Workspace(tenant.getBusinessName(), tenant.getCountryCode());
        }
    }

    @Component
    static class LibPhoneNumberFormat implements PhoneNumberFormat {
        private final InternationalPhoneNumberService phoneNumbers;

        LibPhoneNumberFormat(InternationalPhoneNumberService phoneNumbers) {
            this.phoneNumbers = phoneNumbers;
        }

        @Override
        public String international(String typed, String defaultCountryCode) {
            return phoneNumbers.normalize(typed, defaultCountryCode);
        }
    }

    /** Booking tools use each service's own length. */
    @Component
    static class ProfileServiceDurations implements com.sauti.tool.ServiceDurations {
        private final BusinessProfileService profiles;

        ProfileServiceDurations(BusinessProfileService profiles) {
            this.profiles = profiles;
        }

        @Override
        public java.util.OptionalInt minutesFor(UUID workspaceId, String service) {
            return profiles.serviceDuration(workspaceId, service);
        }
    }

    /** The business profile in a workspace data export. */
    @Component
    static class BusinessProfileExporter implements com.sauti.tenant.application.port.WorkspaceDataExporter {
        private final BusinessProfileService profiles;

        BusinessProfileExporter(BusinessProfileService profiles) {
            this.profiles = profiles;
        }

        @Override
        public String section() {
            return "businessProfile";
        }

        @Override
        public Object export(UUID workspaceId) {
            return profiles.get(workspaceId);
        }
    }

    /** Agents read workspace facts through their own port, so the agent module does not depend on this one. */
    @Component
    static class AgentBusinessFacts implements BusinessFacts {
        private final BusinessProfileService profiles;

        AgentBusinessFacts(BusinessProfileService profiles) {
            this.profiles = profiles;
        }

        @Override
        public Map<String, String> factsFor(UUID workspaceId) {
            return profiles.facts(workspaceId);
        }
    }
}
