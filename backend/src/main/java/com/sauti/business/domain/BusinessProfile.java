package com.sauti.business.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The facts about a business that every one of its agents may tell callers, entered once per
 * workspace. An agent can still override any of them with its own variable value.
 */
@Entity
@Table(name = "business_profiles")
public class BusinessProfile {
    public static final int MAX_SERVICES = 100;
    public static final int MAX_FAQS = 50;
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
    private static final Pattern WEBSITE = Pattern.compile("(?i)(https?://)?[\\w.-]+\\.[a-z]{2,}(/\\S*)?");

    @Id
    @Column(name = "tenant_id")
    private UUID workspaceId;

    @Column(name = "display_name", length = 160)
    private String displayName;

    @Column(length = 500)
    private String description;

    @Column(length = 500)
    private String address;

    @Column(length = 40)
    private String phone;

    @Column(length = 254)
    private String email;

    @Column(length = 300)
    private String website;

    @Column(name = "cancellation_policy", length = 2000)
    private String cancellationPolicy;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "business_profile_services", joinColumns = @JoinColumn(name = "tenant_id"))
    @OrderColumn(name = "position")
    private List<ServiceOffering> services = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "business_profile_faqs", joinColumns = @JoinColumn(name = "tenant_id"))
    @OrderColumn(name = "position")
    private List<FaqEntry> faqs = new ArrayList<>();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected BusinessProfile() {
    }

    public BusinessProfile(UUID workspaceId, OffsetDateTime now) {
        this.workspaceId = workspaceId;
        this.updatedAt = now;
    }

    /** Replaces every fact at once; the phone number must already be in international form. */
    public void update(Details details, OffsetDateTime now) {
        var cleanEmail = Text.clean(details.email());
        if (cleanEmail != null && !EMAIL.matcher(cleanEmail).matches()) {
            throw new BusinessProfileException("Enter a valid email address");
        }
        var cleanWebsite = Text.clean(details.website());
        if (cleanWebsite != null && !WEBSITE.matcher(cleanWebsite).matches()) {
            throw new BusinessProfileException("Enter a valid website, such as brightsmile.co.ke");
        }
        var newServices = details.services() == null ? List.<ServiceOffering>of() : details.services();
        var newFaqs = details.faqs() == null ? List.<FaqEntry>of() : details.faqs();
        if (newServices.size() > MAX_SERVICES) throw new BusinessProfileException("Add at most " + MAX_SERVICES + " services");
        if (newFaqs.size() > MAX_FAQS) throw new BusinessProfileException("Add at most " + MAX_FAQS + " FAQs");
        this.displayName = Text.limit(Text.clean(details.displayName()), 160, "Business names");
        this.description = Text.limit(Text.clean(details.description()), 500, "Descriptions");
        this.address = Text.limit(Text.clean(details.address()), 500, "Addresses");
        this.phone = Text.limit(Text.clean(details.phone()), 40, "Phone numbers");
        this.email = Text.limit(cleanEmail, 254, "Email addresses");
        this.website = Text.limit(cleanWebsite, 300, "Websites");
        this.cancellationPolicy = Text.limit(Text.clean(details.cancellationPolicy()), 2000, "Cancellation policies");
        this.services.clear();
        this.services.addAll(newServices);
        this.faqs.clear();
        this.faqs.addAll(newFaqs);
        this.updatedAt = now;
    }

    /**
     * The facts as prompt values, keyed like agent variables, so a {{business_address}} in any
     * agent's prompt is filled from here when the agent has no value of its own. Empty facts are absent.
     */
    public Map<String, String> facts(String workspaceName) {
        var facts = new LinkedHashMap<String, String>();
        put(facts, "business_name", displayName == null ? Text.clean(workspaceName) : displayName);
        put(facts, "business_description", description);
        put(facts, "business_address", address);
        put(facts, "business_phone", phone);
        put(facts, "business_email", email);
        put(facts, "business_website", website);
        if (!services.isEmpty()) {
            var catalog = services.stream().map(ServiceOffering::spoken).collect(Collectors.joining("\n"));
            put(facts, "services", catalog);
            put(facts, "services_and_prices", catalog);
        }
        if (!faqs.isEmpty()) {
            put(facts, "faq", faqs.stream()
                    .map(entry -> "Q: " + entry.getQuestion() + " A: " + entry.getAnswer())
                    .collect(Collectors.joining("\n")));
        }
        put(facts, "cancellation_policy", cancellationPolicy);
        return facts;
    }

    /**
     * The configured length of the service a caller named. An exact name wins; otherwise one service
     * whose name contains the request, or is contained in it, counts. Several such services, or a
     * service without a duration, give nothing, so an unclear request never picks a wrong length.
     */
    public java.util.OptionalInt durationFor(String requested) {
        var wanted = normalize(requested);
        if (wanted.isEmpty()) return java.util.OptionalInt.empty();
        // Most specific first: the exact name, then names containing the request, then names inside it.
        var tiers = List.<java.util.function.Predicate<String>>of(
                name -> name.equals(wanted), name -> name.contains(wanted), wanted::contains);
        var matches = List.<ServiceOffering>of();
        for (var tier : tiers) {
            matches = services.stream().filter(service -> tier.test(normalize(service.getName()))).toList();
            if (!matches.isEmpty()) break;
        }
        if (matches.size() != 1 || matches.get(0).getDurationMinutes() == null) return java.util.OptionalInt.empty();
        return java.util.OptionalInt.of(matches.get(0).getDurationMinutes());
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return value.toLowerCase(java.util.Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    private static void put(Map<String, String> facts, String key, String value) {
        if (value != null && !value.isBlank()) facts.put(key, value);
    }

    public UUID getWorkspaceId() { return workspaceId; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public String getAddress() { return address; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getWebsite() { return website; }
    public String getCancellationPolicy() { return cancellationPolicy; }
    public List<ServiceOffering> getServices() { return List.copyOf(services); }
    public List<FaqEntry> getFaqs() { return List.copyOf(faqs); }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    /** Everything an owner edits, in one value. */
    public record Details(
            String displayName,
            String description,
            String address,
            String phone,
            String email,
            String website,
            String cancellationPolicy,
            List<ServiceOffering> services,
            List<FaqEntry> faqs
    ) {
    }
}
