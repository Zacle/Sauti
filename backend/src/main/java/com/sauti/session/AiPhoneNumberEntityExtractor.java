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
            Use complete only when the caller clearly supplied a finished full number in this utterance.
            Use incomplete with the clear digits when the caller supplied an unambiguous partial group and intends
            to continue in another turn. Use unclear with no digits when any spoken digit itself is ambiguous.
            """;

    private final LlmToolCallingProvider provider;

    public AiPhoneNumberEntityExtractor(LlmToolCallingProvider provider) {
        this.provider = provider;
    }

    @Override
    public String extract(Call call, String sourceUtterance, String candidate) {
        var extraction = extractSequence(call, sourceUtterance, candidate);
        return extraction.complete() && extraction.digits().length() >= 7
                ? extraction.digits()
                : "";
    }

    @Override
    public Extraction extractSequence(Call call, String sourceUtterance, String candidate) {
        if (call == null) return Extraction.unclear();
        var source = clean(sourceUtterance);
        var fallback = clean(candidate);
        if (source.isBlank()) return Extraction.unclear();
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
                    .map(tool -> extraction(tool.arguments()))
                    .orElseGet(Extraction::unclear);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Phone-digit semantic extraction failed callId={} exception={}",
                    call.getId(),
                    exception.getClass().getSimpleName()
            );
            return Extraction.unclear();
        }
    }

    private static Extraction extraction(Map<String, Object> arguments) {
        var status = text(arguments, "status").toLowerCase(java.util.Locale.ROOT);
        var digits = digitString(arguments.get("digits"));
        if (digits.length() > 15 || "unclear".equals(status)) return Extraction.unclear();
        if (digits.isBlank()) return Extraction.unclear();
        if ("complete".equals(status) && digits.length() < 7) status = "incomplete";
        return new Extraction(status, digits);
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
