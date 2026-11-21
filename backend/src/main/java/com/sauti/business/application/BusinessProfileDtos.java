package com.sauti.business.application;

import com.sauti.business.domain.BusinessProfile;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class BusinessProfileDtos {
    private BusinessProfileDtos() {
    }

    public record BusinessProfileRequest(
            @Size(max = 160) String displayName,
            @Size(max = 500) String description,
            @Size(max = 500) String address,
            @Size(max = 40) String phone,
            @Size(max = 254) String email,
            @Size(max = 300) String website,
            @Size(max = 2000) String cancellationPolicy,
            @Size(max = BusinessProfile.MAX_SERVICES) List<@Valid ServiceItem> services,
            @Size(max = BusinessProfile.MAX_FAQS) List<@Valid FaqItem> faqs
    ) {
        List<ServiceItem> servicesOrEmpty() {
            return BusinessProfileService.orEmpty(services);
        }

        List<FaqItem> faqsOrEmpty() {
            return BusinessProfileService.orEmpty(faqs);
        }
    }

    public record ServiceItem(
            @Size(max = 120) String name,
            BigDecimal priceAmount,
            @Size(max = 3) String currency,
            Integer durationMinutes,
            @Size(max = 300) String description
    ) {
    }

    public record FaqItem(@Size(max = 300) String question, @Size(max = 1000) String answer) {
    }

    /**
     * @param workspaceName used for the business name when displayName is empty
     */
    public record BusinessProfileView(
            String workspaceName,
            String displayName,
            String description,
            String address,
            String phone,
            String email,
            String website,
            String cancellationPolicy,
            List<ServiceItem> services,
            List<FaqItem> faqs,
            /** The values agents receive, keyed like agent variables (business_address, services...). */
            java.util.Map<String, String> facts,
            OffsetDateTime updatedAt
    ) {
        static BusinessProfileView from(BusinessProfile profile, String workspaceName) {
            return new BusinessProfileView(
                    workspaceName, profile.getDisplayName(), profile.getDescription(), profile.getAddress(),
                    profile.getPhone(), profile.getEmail(), profile.getWebsite(), profile.getCancellationPolicy(),
                    profile.getServices().stream().map(service -> new ServiceItem(service.getName(),
                            service.getPriceAmount(), service.getCurrency(), service.getDurationMinutes(),
                            service.getDescription())).toList(),
                    profile.getFaqs().stream().map(faq -> new FaqItem(faq.getQuestion(), faq.getAnswer())).toList(),
                    profile.facts(workspaceName),
                    profile.getUpdatedAt()
            );
        }

        static BusinessProfileView empty(String workspaceName) {
            return new BusinessProfileView(workspaceName, null, null, null, null, null, null, null,
                    List.of(), List.of(), workspaceName == null || workspaceName.isBlank()
                            ? java.util.Map.of() : java.util.Map.of("business_name", workspaceName), null);
        }
    }
}
