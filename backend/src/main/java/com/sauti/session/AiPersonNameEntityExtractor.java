package com.sauti.session;

import com.sauti.call.Call;
import com.sauti.llm.AgentContext;
import com.sauti.llm.ConversationMessage;
import com.sauti.llm.LlmToolCallingProvider;
import com.sauti.llm.LlmToolDefinition;
import com.sauti.llm.LlmToolTurnContext;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AiPersonNameEntityExtractor implements PersonNameEntityExtractor {
    private static final Logger LOGGER = LoggerFactory.getLogger(AiPersonNameEntityExtractor.class);
    private static final String TOOL_NAME = "return_person_name_entity";
    private static final LlmToolDefinition EXTRACTION_TOOL = new LlmToolDefinition(
            TOOL_NAME,
            "Return the semantic person-name entity from the supplied multilingual candidate.",
            Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "status", Map.of(
                                    "type", "string",
                                    "enum", List.of("complete", "incomplete")
                            ),
                            "name", Map.of(
                                    "type", "string",
                                    "description", "Only the complete person's name in its original script and with its original diacritics. Exclude every introduction, carrier phrase, greeting, title not belonging to the name, and all surrounding sentence text."
                            )
                    ),
                    "required", List.of("status", "name"),
                    "additionalProperties", false
            ),
            false
    );
    private static final String SYSTEM_PROMPT = """
            You are a strict multilingual person-name entity extractor.
            The user message is a candidate value supplied for a structured person-name field.
            Call return_person_name_entity exactly once.
            Understand the candidate semantically in whatever language or script it uses.
            If it contains an introduction or sentence, return only the complete person-name entity.
            Preserve the name's original script and diacritics; never translate or transliterate it.
            If no actual complete person name is present, return status=incomplete and an empty name.
            """;

    private final LlmToolCallingProvider provider;

    public AiPersonNameEntityExtractor(LlmToolCallingProvider provider) {
        this.provider = provider;
    }

    @Override
    public String extract(Call call, String candidate) {
        if (call == null || candidate == null || candidate.isBlank()) return "";
        try {
            var language = call.getLanguageDetected() == null || call.getLanguageDetected().isBlank()
                    ? call.getAgent().getDefaultLanguage()
                    : call.getLanguageDetected();
            var response = provider.completeTurn(new LlmToolTurnContext(
                    AgentContext.from(call.getAgent()),
                    SYSTEM_PROMPT,
                    language,
                    List.of(new ConversationMessage("user", candidate.trim())),
                    candidate.trim(),
                    call.getCallerNumber(),
                    call.getId(),
                    call.getTwilioCallSid(),
                    List.of(EXTRACTION_TOOL),
                    List.of(),
                    TOOL_NAME
            ));
            return response.toolCalls().stream()
                    .filter(tool -> TOOL_NAME.equals(tool.name()))
                    .findFirst()
                    .filter(tool -> "complete".equalsIgnoreCase(
                            text(tool.arguments(), "status")
                    ))
                    .map(tool -> PersonNameNormalizer.normalize(
                            text(tool.arguments(), "name")
                    ))
                    .orElse("");
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Person-name semantic extraction failed callId={} exception={}",
                    call.getId(),
                    exception.getClass().getSimpleName()
            );
            return "";
        }
    }

    private static String text(Map<String, Object> arguments, String key) {
        if (arguments == null) return "";
        var value = arguments.get(key);
        return value == null ? "" : value.toString().trim();
    }
}
