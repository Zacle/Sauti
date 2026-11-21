package com.sauti.business.application;

import com.sauti.business.application.BusinessProfileDtos.BusinessProfileRequest;
import com.sauti.business.application.BusinessProfileDtos.BusinessProfileView;
import com.sauti.business.application.port.PhoneNumberFormat;
import com.sauti.business.application.port.WorkspaceLookup;
import com.sauti.business.domain.BusinessProfile;
import com.sauti.business.domain.BusinessProfileRepository;
import com.sauti.business.domain.FaqEntry;
import com.sauti.business.domain.ServiceOffering;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads and edits a workspace's business profile, and supplies its facts to agents. */
@Service
public class BusinessProfileService {
    private final BusinessProfileRepository profiles;
    private final WorkspaceLookup workspaces;
    private final PhoneNumberFormat phoneNumbers;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Autowired
    public BusinessProfileService(BusinessProfileRepository profiles, WorkspaceLookup workspaces,
                                  PhoneNumberFormat phoneNumbers, ApplicationEventPublisher events) {
        this(profiles, workspaces, phoneNumbers, events, Clock.systemUTC());
    }

    BusinessProfileService(BusinessProfileRepository profiles, WorkspaceLookup workspaces,
                           PhoneNumberFormat phoneNumbers, ApplicationEventPublisher events, Clock clock) {
        this.profiles = profiles;
        this.workspaces = workspaces;
        this.phoneNumbers = phoneNumbers;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public BusinessProfileView get(UUID workspaceId) {
        var workspace = workspaces.require(workspaceId);
        return profiles.findById(workspaceId)
                .map(profile -> BusinessProfileView.from(profile, workspace.name()))
                .orElseGet(() -> BusinessProfileView.empty(workspace.name()));
    }

    @Transactional
    public BusinessProfileView update(UUID workspaceId, BusinessProfileRequest request) {
        var workspace = workspaces.require(workspaceId);
        var now = OffsetDateTime.now(clock);
        var profile = profiles.findById(workspaceId).orElseGet(() -> new BusinessProfile(workspaceId, now));
        var phone = request.phone() == null || request.phone().isBlank()
                ? null
                : phoneNumbers.international(request.phone(), workspace.countryCode());
        profile.update(new BusinessProfile.Details(
                request.displayName(), request.description(), request.address(), phone, request.email(),
                request.website(), request.cancellationPolicy(),
                request.servicesOrEmpty().stream().map(service -> new ServiceOffering(
                        service.name(), service.priceAmount(), service.currency(), service.durationMinutes(),
                        service.description())).toList(),
                request.faqsOrEmpty().stream().map(faq -> new FaqEntry(faq.question(), faq.answer())).toList()
        ), now);
        var saved = profiles.save(profile);
        events.publishEvent(new BusinessProfileChanged(workspaceId));
        return BusinessProfileView.from(saved, workspace.name());
    }

    /** Prompt values for every agent in the workspace, keyed like agent variables. */
    @Transactional(readOnly = true)
    public Map<String, String> facts(UUID workspaceId) {
        // Same as get(...).facts(): before a profile is saved, the workspace name is the business name.
        return get(workspaceId).facts();
    }

    @Transactional(readOnly = true)
    public java.util.OptionalInt serviceDuration(UUID workspaceId, String service) {
        return profiles.findById(workspaceId)
                .map(profile -> profile.durationFor(service))
                .orElse(java.util.OptionalInt.empty());
    }

    static <T> List<T> orEmpty(List<T> values) {
        return values == null ? List.of() : values;
    }
}
