package com.sauti.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class SystemAgentTemplateSeederTest {
    @Test
    void parsesAllDocumentTemplatesAndDropsUnsupportedLanguageLabels() throws Exception {
        var resource = new ClassPathResource("templates/agent-templates.md");
        String markdown;
        try (var input = resource.getInputStream()) {
            markdown = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        var seeder = new SystemAgentTemplateSeeder(null, new ObjectMapper());

        var templates = seeder.parse(markdown);

        assertThat(templates).hasSize(10);
        assertThat(templates).extracting(template -> template.name())
                .contains("Medical Receptionist", "Dental Front Desk", "Financial Services Advisor");
        var dental = templates.stream()
                .filter(template -> template.name().equals("Dental Front Desk"))
                .findFirst()
                .orElseThrow();
        assertThat(dental.supportedLanguages()).containsExactly("en", "fr");
        assertThat(dental.configurationJson())
                .contains(
                        "\"bookingEnabled\":true",
                        "\"key\":\"clinic_name\"",
                        "\"label\":\"Clinic name\"",
                        "\"required\":true"
                )
                .doesNotContain("\"key\":\"agent_name\"", "\"key\":\"timezone\"");
        assertThat(dental.systemPrompt()).contains("Dental Emergency", "{{dentist_names}}");
    }
}
