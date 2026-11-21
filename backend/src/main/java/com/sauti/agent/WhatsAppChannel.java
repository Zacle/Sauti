package com.sauti.agent;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** WhatsApp conversations: whether they are on, and the business number that receives them. */
@Embeddable
public class WhatsAppChannel {
    @Column(name = "whatsapp_enabled", nullable = false)
    private boolean enabled;

    @Column(name = "whatsapp_phone_number_id", unique = true, length = 100)
    private String phoneNumberId;

    protected WhatsAppChannel() {
    }

    static WhatsAppChannel off() {
        return new WhatsAppChannel();
    }

    void configure(Boolean enabled, String phoneNumberId) {
        this.enabled = enabled != null && enabled;
        this.phoneNumberId = phoneNumberId == null || phoneNumberId.isBlank() ? null : phoneNumberId.trim();
    }

    public boolean isEnabled() { return enabled; }
    public String getPhoneNumberId() { return phoneNumberId; }
}
