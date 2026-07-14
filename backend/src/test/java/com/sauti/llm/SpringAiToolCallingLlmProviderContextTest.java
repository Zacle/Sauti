package com.sauti.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.chat.messages.AssistantMessage;

@SpringBootTest(properties = {
        "sauti.llm.provider=spring-ai",
        "sauti.llm.primary-model=gpt-5.4-mini",
        "sauti.llm.fallback-model=gemini-3.1-flash-lite",
        "spring.ai.google.genai.api-key=test-google-key",
        "spring.ai.openai.api-key=test-openai-key"
})
class SpringAiToolCallingLlmProviderContextTest {

    @Autowired
    private LlmToolCallingProvider provider;

    @Test
    void loadsOpenAiPrimaryWithGeminiFallbackProvider() {
        assertThat(provider).isInstanceOf(SpringAiToolCallingLlmProvider.class);
    }

    @Test
    void toolCallbackReturnsControlledErrorWhenInvokedInternally() throws Exception {
        var method = SpringAiToolCallingLlmProvider.class.getDeclaredMethod("toolCallback", LlmToolDefinition.class);
        method.setAccessible(true);
        var callback = (ToolCallback) method.invoke(provider, new LlmToolDefinition(
                "check_availability",
                "Check available appointments",
                Map.of("type", "object")
        ));

        assertThat(callback.call("{}"))
                .contains("Tool execution is handled by Sauti")
                .contains("check_availability");
    }

    @Test
    void missingToolCallsAreTreatedAsNoToolCalls() {
        var springProvider = (SpringAiToolCallingLlmProvider) provider;
        var output = AssistantMessage.builder().content("Bonjour Zachary, comment puis-je vous aider ?").build();

        assertThat(springProvider.toolCalls(output)).isEmpty();
    }

    @Test
    void convertsCumulativeStreamingUpdatesIntoExactIncrementalSpeech() {
        var accumulated = new StringBuilder();

        assertThat(SpringAiToolCallingLlmProvider.incrementalText(accumulated, "Bonjour"))
                .isEqualTo("Bonjour");
        assertThat(SpringAiToolCallingLlmProvider.incrementalText(accumulated, "Bonjour, comment"))
                .isEqualTo(", comment");
        assertThat(SpringAiToolCallingLlmProvider.incrementalText(accumulated, " puis-je vous aider ?"))
                .isEqualTo(" puis-je vous aider ?");
        assertThat(accumulated.toString()).isEqualTo("Bonjour, comment puis-je vous aider ?");
    }

    @Test
    void toolSchemasStripFormatHintsBeforeProviderSubmission() {
        var springProvider = (SpringAiToolCallingLlmProvider) provider;

        var schema = springProvider.sanitizeSchema(Map.of(
                "type", "object",
                "properties", Map.of(
                        "caller_phone", Map.of(
                                "type", "string",
                                "description", "Caller phone",
                                "format", "phone"
                        ),
                        "appointment_at", Map.of(
                                "type", "string",
                                "description", "Appointment time",
                                "format", "date-time"
                        )
                )
        ));

        assertThat(schema.toString()).doesNotContain("format");
        assertThat(schema.toString()).contains("caller_phone", "appointment_at");
    }

}
