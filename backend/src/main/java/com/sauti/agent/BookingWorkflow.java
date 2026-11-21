package com.sauti.agent;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Whether the agent books appointments, what it must collect first, how long a booking lasts when
 * the service has no duration of its own, and who is told about new bookings.
 */
@Embeddable
public class BookingWorkflow {
    /** Always collected when booking is on; owners can add fields but not remove these. */
    static final List<String> CORE_FIELDS = List.of("caller_name", "caller_phone", "service_type", "appointment_at");
    private static final List<String> CHANNELS = List.of("dashboard", "email");
    private static final int MAX_FIELDS = 25;

    @Column(name = "booking_enabled", nullable = false)
    private boolean enabled = false;

    @Column(name = "booking_required_fields", nullable = false, columnDefinition = "TEXT")
    private String requiredFields = String.join(",", CORE_FIELDS);

    @Column(name = "default_booking_duration_minutes", nullable = false)
    private int defaultDurationMinutes = 60;

    @Column(name = "booking_notification_channels", nullable = false)
    private String notificationChannels = "dashboard,email";

    @Column(name = "booking_notification_recipient", length = 320)
    private String notificationRecipient;

    protected BookingWorkflow() {
    }

    static BookingWorkflow defaults() {
        return new BookingWorkflow();
    }

    void enable(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * @throws IllegalArgumentException when the duration is outside 5 to 480 minutes, a field key is
     *                                  not snake_case, or more than 25 fields are asked for
     */
    void configure(Integer durationMinutes, List<String> fields, List<String> channels, String recipient) {
        var duration = durationMinutes == null ? 60 : durationMinutes;
        if (duration < 5 || duration > 480) {
            throw new IllegalArgumentException("Default booking duration must be between 5 and 480 minutes");
        }
        var configured = fields == null ? List.<String>of() : fields.stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .peek(value -> {
                    if (!value.matches("^[a-z][a-z0-9_]{0,63}$")) {
                        throw new IllegalArgumentException("Booking field keys must use lowercase snake_case");
                    }
                })
                .distinct()
                .toList();
        if (configured.size() > MAX_FIELDS) {
            throw new IllegalArgumentException("An agent may require at most 25 booking fields");
        }
        var all = new ArrayList<String>();
        if (enabled) all.addAll(CORE_FIELDS);
        all.addAll(configured);
        var notify = channels == null ? List.<String>of() : channels.stream()
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .filter(CHANNELS::contains)
                .distinct()
                .toList();
        this.defaultDurationMinutes = duration;
        this.requiredFields = String.join(",", all.stream().distinct().toList());
        this.notificationChannels = String.join(",", notify.isEmpty() ? List.of("dashboard") : notify);
        this.notificationRecipient = recipient == null || recipient.isBlank() ? null : recipient.trim();
    }

    public boolean isEnabled() { return enabled; }
    public int getDefaultDurationMinutes() { return defaultDurationMinutes; }
    public List<String> getRequiredFields() { return Ranges.split(requiredFields); }
    public List<String> getNotificationChannels() { return Ranges.split(notificationChannels); }
    public String getNotificationRecipient() { return notificationRecipient; }
}
