package com.sauti.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sauti.agent.Agent;
import com.sauti.calendar.Booking;
import com.sauti.tenant.Tenant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WhatsAppTemplateParameterMapperTest {
    private final WhatsAppTemplateParameterMapper mapper = new WhatsAppTemplateParameterMapper();

    @Test
    void mapsTrustedBookingFieldsInMetaPlaceholderOrder() {
        var booking = booking();
        var configuration = Map.<String, Object>of(
                "templateLanguage", "en_US",
                "templateParameterFormat", "POSITIONAL",
                "templateParameters", List.of(
                        parameter("body.2", "body", 2, "2"),
                        parameter("body.1", "body", 1, "1")
                ),
                "templateParameterMappings", Map.of(
                        "body.1", "customer_name",
                        "body.2", "booking_reference"
                )
        );

        assertThat(mapper.components(configuration, booking)).containsExactly(Map.of(
                "type", "body",
                "parameters", List.of(
                        Map.of("type", "text", "text", "Alexandria"),
                        Map.of("type", "text", "text", "SAT-ABC123")
                )
        ));
    }

    @Test
    void includesParameterNamesForNamedTemplates() {
        var configuration = Map.<String, Object>of(
                "templateLanguage", "en_US",
                "templateParameterFormat", "NAMED",
                "templateParameters", List.of(parameter("body.customer", "body", 1, "customer")),
                "templateParameterMappings", Map.of("body.customer", "customer_name")
        );

        assertThat(mapper.components(configuration, booking()))
                .singleElement().satisfies(component -> assertThat(component.get("parameters"))
                        .isEqualTo(List.of(Map.of(
                                "type", "text", "text", "Alexandria", "parameter_name", "customer"))));
    }

    @Test
    void refusesToSendWhenAPlaceholderHasNoMapping() {
        var configuration = Map.<String, Object>of(
                "templateParameters", List.of(parameter("body.1", "body", 1, "1")),
                "templateParameterMappings", Map.of()
        );

        assertThatThrownBy(() -> mapper.components(configuration, booking()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("body.1");
    }

    private Booking booking() {
        var booking = mock(Booking.class);
        var agent = mock(Agent.class);
        var tenant = mock(Tenant.class);
        when(booking.getAgent()).thenReturn(agent);
        when(booking.getTenant()).thenReturn(tenant);
        when(agent.getTimezone()).thenReturn("Africa/Cairo");
        when(agent.getName()).thenReturn("Ailsa");
        when(tenant.getBusinessName()).thenReturn("Hairy");
        when(booking.getCallerName()).thenReturn("Alexandria");
        when(booking.getCallerPhone()).thenReturn("+201011575244");
        when(booking.getCallerEmail()).thenReturn("alexandria@example.com");
        when(booking.getServiceType()).thenReturn("women hairstyle");
        when(booking.getAppointmentAt()).thenReturn(OffsetDateTime.parse("2026-08-11T10:00:00+03:00"));
        when(booking.getBookingReference()).thenReturn("SAT-ABC123");
        when(booking.getDurationMinutes()).thenReturn(60);
        return booking;
    }

    private Map<String, Object> parameter(String key, String component, int position, String name) {
        return Map.of("key", key, "component", component, "position", position,
                "name", name, "placeholder", "{{" + name + "}}");
    }
}
