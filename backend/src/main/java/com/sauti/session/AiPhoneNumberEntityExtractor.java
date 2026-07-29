package com.sauti.session;

import com.sauti.call.Call;
import com.sauti.llm.AgentContext;
import com.sauti.llm.ConversationMessage;
import com.sauti.llm.LlmToolCallingProvider;
import com.sauti.llm.LlmToolDefinition;
import com.sauti.llm.LlmToolTurnContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AiPhoneNumberEntityExtractor implements PhoneNumberEntityExtractor {
    private static final Logger LOGGER = LoggerFactory.getLogger(AiPhoneNumberEntityExtractor.class);
    private static final String TOOL_NAME = "return_phone_digit_sequence";
    private static final List<String> DIGITS = List.of(
            "0", "1", "2", "3", "4", "5", "6", "7", "8", "9"
    );
    private static final LlmToolDefinition EXTRACTION_TOOL = new LlmToolDefinition(
            TOOL_NAME,
            "Return the exact ordered phone digits explicitly spoken in the latest caller utterance.",
            Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "status", Map.of(
                                    "type", "string",
                                    "enum", List.of("complete", "incomplete", "unclear")
                            ),
                            "digits", Map.of(
                                    "type", "array",
                                    "description", "Every explicitly spoken phone digit exactly once and in order. Never infer, duplicate, omit, or regroup a digit.",
                                    "items", Map.of("type", "string", "enum", DIGITS),
                                    "minItems", 0,
                                    "maxItems", 15
                            )
                    ),
                    "required", List.of("status", "digits"),
                    "additionalProperties", false
            ),
            false
    );
    private static final String SYSTEM_PROMPT = """
            You are a strict multilingual phone-digit extractor.
            Call return_phone_digit_sequence exactly once.
            SOURCE_UTTERANCE is authoritative. Understand number words in whatever language or script the caller used.
            Return every phone digit explicitly spoken in the caller's final complete sequence exactly once and in order.
            When the caller restarts or corrects themselves, use the final clearly completed corrected sequence.
            Never repair a number from typical formatting, country rules, length, or the MODEL_CANDIDATE.
            MODEL_CANDIDATE is provided only to expose possible disagreement. Never use it as a substitute for
            SOURCE_UTTERANCE and never copy digits from it.
            If any digit or the end of the sequence is ambiguous, return unclear or incomplete and no digits.
            """;

    private final LlmToolCallingProvider provider;

    public AiPhoneNumberEntityExtractor(LlmToolCallingProvider provider) {
        this.provider = provider;
    }

    @Override
    public String extract(Call call, String sourceUtterance, String candidate) {
        if (call == null) return "";
        var source = clean(sourceUtterance);
        var fallback = clean(candidate);
        if (source.isBlank()) return "";
        try {
            var language = call.getLanguageDetected() == null || call.getLanguageDetected().isBlank()
                    ? call.getAgent().getDefaultLanguage()
                    : call.getLanguageDetected();
            var input = "SOURCE_UTTERANCE:\n" + source + "\n\nMODEL_CANDIDATE:\n" + fallback;
            var response = provider.completeTurn(new LlmToolTurnContext(
                    AgentContext.from(call.getAgent()),
                    SYSTEM_PROMPT,
                    language,
                    List.of(new ConversationMessage("user", input)),
                    source,
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
                    .filter(tool -> "complete".equalsIgnoreCase(text(tool.arguments(), "status")))
                    .map(tool -> digitString(tool.arguments().get("digits")))
                    .filter(value -> value.length() >= 7 && value.length() <= 15)
                    .orElse("");
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Phone-digit semantic extraction failed callId={} exception={}",
                    call.getId(),
                    exception.getClass().getSimpleName()
            );
            return "";
        }
    }

    private static String digitString(Object raw) {
        if (!(raw instanceof List<?> values)) return "";
        var digits = new ArrayList<String>();
        for (var value : values) {
            var digit = clean(value);
            if (!DIGITS.contains(digit)) return "";
            digits.add(digit);
        }
        return String.join("", digits);
    }

    private static String text(Map<String, Object> arguments, String key) {
        if (arguments == null) return "";
        return clean(arguments.get(key));
    }

    private static String clean(Object value) {
        return value == null ? "" : value.toString().trim();
    }
}
