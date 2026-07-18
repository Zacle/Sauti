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

        assertThat(templates).hasSize(6);
        assertThat(templates).extracting(template -> template.name())
                .contains("Medical Receptionist", "Dental Front Desk", "Fitness Membership & Class Desk");
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
    }
}
