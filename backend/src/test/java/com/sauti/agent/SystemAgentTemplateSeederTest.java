package com.sauti.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class SystemAgentTemplateSeederTest {
    @Test
    void parsesLayeredMultilingualTemplates() throws Exception {
        var resource = new ClassPathResource("templates/agent-templates.md");
        String markdown;
        try (var input = resource.getInputStream()) {
            markdown = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        var seeder = new SystemAgentTemplateSeeder(null, new ObjectMapper());

        var templates = seeder.parse(markdown);

        assertThat(templates).hasSize(22);
        assertThat(templates).extracting(template -> template.name())
                .contains(
                        "Medical Receptionist",
                        "Dental Front Desk",
                        "Fitness Membership & Class Desk",
                        "Auto Repair Advisor",
                        "Restaurant Reservation Host",
                        "Veterinary Clinic Receptionist",
                        "General Support Helpdesk",
                        "Order Status and Returns Desk",
                        "IT and SaaS Technical Support Tier 1",
                        "Real Estate Lead Qualifier",
                        "Insurance Sales Intake",
                        "B2B SaaS Demo Booking SDR",
                        "E-commerce Cart Recovery Specialist"
                );
        var dental = templates.stream()
                .filter(template -> template.name().equals("Dental Front Desk"))
                .findFirst()
                .orElseThrow();
        assertThat(dental.supportedLanguages()).containsExactly("en", "fr", "ar", "sw");
        assertThat(dental.configurationJson())
                .contains(
                        "\"schemaVersion\":2",
                        "\"bookingEnabled\":true",
                        "\"key\":\"business_name\"",
                        "\"key\":\"dental_urgency_policy\"",
                        "\"bookingRequiredFields\":[\"caller_name\",\"caller_phone\",\"patient_status\",\"service_type\",\"appointment_at\"]",
                        "\"required\":true"
                );
        assertThat(dental.systemPrompt()).contains("{{dentists}}", "{{dental_urgency_policy}}");

        var autoRepair = template(templates, "Auto Repair Advisor");
        assertThat(autoRepair.configurationJson()).contains(
                "\"group\":\"Appointments\"",
                "\"icon\":\"car\"",
                "\"key\":\"vehicle_safety_rules\"",
                "\"bookingRequiredFields\":[\"caller_name\",\"caller_phone\",\"vehicle_make_model\""
        );

        var helpdesk = template(templates, "General Support Helpdesk");
        assertThat(helpdesk.configurationJson()).contains(
                "\"group\":\"Support\"",
                "\"bookingEnabled\":false",
                "\"key\":\"ticket_fields\""
        );

        var demo = template(templates, "B2B SaaS Demo Booking SDR");
        assertThat(demo.configurationJson()).contains(
                "\"group\":\"Sales\"",
                "\"bookingEnabled\":true",
                "\"icon\":\"demo\""
        );
    }

    private AgentTemplateDtos.AgentTemplateRequest template(
            java.util.List<AgentTemplateDtos.AgentTemplateRequest> templates,
            String name
    ) {
        return templates.stream()
                .filter(template -> template.name().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
