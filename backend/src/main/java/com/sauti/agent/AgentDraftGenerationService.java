package com.sauti.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.agent.AgentDraftGenerationDtos.GeneratedAgentDraftResponse;
import com.sauti.agent.AgentDraftGenerationDtos.GeneratedVariable;
import com.google.genai.Client;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AgentDraftGenerationService {
    private static final int GEMINI_THINKING_BUDGET = 0;
    private static final Pattern VARIABLE_KEY = Pattern.compile("^[a-z][a-z0-9_]{0,99}$");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([a-z][a-z0-9_]*)}}");
    private static final Pattern MARKDOWN_HEADING = Pattern.compile("(?m)^##\\s+\\S+");
    private static final Pattern NUMBERED_STEP = Pattern.compile("(?m)^1\\.\\s+\\S+");
    private static final List<String> ALLOWED_LANGUAGES = List.of("sw", "en", "fr", "ar");
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper;
    private final String provider;
    private final String webhookUrl;
    private final String webhookSecret;
    private final String modelName;
    private final ChatModel geminiModel;

    public AgentDraftGenerationService(
            ObjectMapper objectMapper,
            @Value("${sauti.llm.provider:heuristic}") String provider,
            @Value("${sauti.llm.webhook.draft-url:http://localhost:8090/agent-draft}") String webhookUrl,
            @Value("${sauti.llm.webhook.secret:}") String webhookSecret,
            @Value("${sauti.llm.default-model:gemini-2.5-flash}") String modelName,
            @Value("${spring.ai.google.genai.api-key:}") String googleApiKey
    ) {
        this.objectMapper = objectMapper;
        this.provider = provider;
        this.webhookUrl = webhookUrl;
        this.webhookSecret = webhookSecret;
        this.modelName = modelName;
        this.geminiModel = "spring-ai".equalsIgnoreCase(provider) && googleApiKey != null && !googleApiKey.isBlank()
                ? createGeminiModel(googleApiKey, modelName)
                : null;
    }

    public GeneratedAgentDraftResponse generate(String brief) {
        if ("webhook".equalsIgnoreCase(provider)) {
            return generateWithWebhook(brief);
        }
        if ("spring-ai".equalsIgnoreCase(provider)) {
            return generateWithGemini(brief);
        }
        return generateLocally(brief);
    }

    private GeneratedAgentDraftResponse generateWithGemini(String brief) {
        if (geminiModel == null) {
            throw new IllegalStateException("GOOGLE_AI_API_KEY is required to generate an agent with AI");
        }
        var system = """
                You design production-ready multilingual AI phone agents for small and medium businesses.
                Analyze the user's brief and return exactly one agent draft as valid JSON, without a surrounding Markdown code fence.
                The JSON must contain:
                {
                  "name": "short human-readable agent name",
                  "description": "one concise sentence",
                  "greetingMessage": "brief instruction for how the opening should be generated at call time, not exact words to say",
                  "systemPrompt": "a complete Markdown-formatted operating manual for the voice agent",
                  "bookingEnabled": true,
                  "defaultLanguage": "one of sw,en,fr,ar",
                  "supportedLanguages": ["one or more of sw,en,fr,ar"],
                  "escalationPhrases": ["specific caller phrases"],
                  "variables": [
                    {"key":"business_name","label":"Business name","description":"Official business name","required":true}
                  ]
                }

                SYSTEM PROMPT QUALITY REQUIREMENTS:
                - Start with a direct role statement such as: "You are {{agent_name}}, the virtual ... for {{business_name}}."
                - Use at least five descriptive level-two Markdown headings beginning with ##.
                - Include a ## Your Role section defining scope and explicit limitations.
                - Include a ## Business Information section with bullet points for every required business fact.
                - Include one or more domain-specific workflow sections with numbered steps and branches where relevant.
                - Include specific escalation, safety, and prohibited-behavior instructions.
                - Include tool rules. If booking is enabled, require availability checks before offering or confirming times.
                - For booking agents, specify a phone-friendly collection order: service or reason, full name, date, time preference, then contact detail.
                - For healthcare agents, do not collect date of birth, medical history, insurance, symptoms, or other sensitive details unless explicitly required by the brief.
                - Finish with a ## Tone section describing how the agent should communicate.
                - Write detailed, actionable instructions, not a short summary or a collection of generic sentences.
                - Adapt section names and workflows to the exact industry and request.

                Use {{variable_name}} placeholders throughout systemPrompt for business facts the user should provide.
                Every placeholder except {{agent_name}} and {{timezone}} must have one matching variables entry.
                Return only variables actually referenced in systemPrompt. Mark facts required when the agent cannot operate correctly without them.
                The greetingMessage field is direction for the live LLM. It must not be a fixed greeting script, and it must scale across supported languages.
                Do not assume a city, country, currency, emergency number, business name, or opening hours.
                Only enable booking if the brief requires appointments, reservations, or scheduling.
                Keep variables reusable and use lowercase snake_case keys.
                """;
        var userMessage = "Business brief:\n" + brief.trim();
        var generated = requestGeminiDraft(system, userMessage);
        if (!isDetailedPrompt(generated.systemPrompt())) {
            generated = requestGeminiDraft(
                    system,
                    userMessage + """

                            The previous draft was too shallow. Regenerate it as a full operating prompt with at least
                            five ## sections, domain-specific numbered workflows, a business-information variable block,
                            escalation/tool rules, and tone guidance.
                            """
            );
        }
        if (!isDetailedPrompt(generated.systemPrompt())) {
            throw new IllegalStateException("Gemini did not generate a sufficiently detailed agent prompt");
        }
        return validateGenerated(generated);
    }

    private GeneratedAgentDraftResponse requestGeminiDraft(String system, String userMessage) {
        var response = geminiModel.call(new Prompt(
                List.of(new SystemMessage(system), new UserMessage(userMessage)),
                GoogleGenAiChatOptions.builder()
                        .model(modelName)
                        .maxOutputTokens(4096)
                        .thinkingBudget(GEMINI_THINKING_BUDGET)
                        .build()
        ));
        var content = response.getResult().getOutput().getText();
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("Gemini returned an empty agent draft");
        }
        try {
            return objectMapper.readValue(stripCodeFence(content), GeneratedAgentDraftResponse.class);
        } catch (IOException exception) {
            throw new IllegalStateException("Gemini returned an invalid agent draft", exception);
        }
    }

    private boolean isDetailedPrompt(String prompt) {
        if (prompt == null || prompt.length() < 700) return false;
        return MARKDOWN_HEADING.matcher(prompt).results().count() >= 5
                && NUMBERED_STEP.matcher(prompt).find()
                && prompt.contains("{{");
    }

    private GeneratedAgentDraftResponse generateWithWebhook(String brief) {
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "task", "generate_agent_draft",
                    "brief", brief.trim(),
                    "requiredFields", List.of(
                            "name", "description", "greetingMessage", "systemPrompt", "bookingEnabled",
                            "defaultLanguage", "supportedLanguages", "escalationPhrases", "variables"
                    )
            ));
            var request = HttpRequest.newBuilder(URI.create(webhookUrl))
                    .header("Content-Type", "application/json");
            if (!webhookSecret.isBlank()) {
                sign(request, body);
            }
            var response = httpClient.send(
                    request.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Agent draft AI webhook failed with status " + response.statusCode());
            }
            return validateGenerated(objectMapper.readValue(response.body(), GeneratedAgentDraftResponse.class));
        } catch (IOException exception) {
            throw new IllegalStateException("Agent draft AI webhook request failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Agent draft AI webhook request was interrupted", exception);
        }
    }

    private GeneratedAgentDraftResponse generateLocally(String brief) {
        String normalized = brief.trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        boolean booking = containsAny(lower, "book", "appointment", "schedule", "reservation", "calendar");
        boolean urgent = containsAny(lower, "clinic", "medical", "health", "legal", "urgent", "emergency");
        String role = containsAny(lower, "clinic", "medical", "health")
                ? "patient access"
                : containsAny(lower, "legal", "law")
                    ? "legal intake"
                    : containsAny(lower, "sales", "lead", "qualif")
                        ? "lead qualification"
                        : booking ? "appointment booking" : "customer support";
        String name = Character.toUpperCase(role.charAt(0)) + role.substring(1) + " Agent";
        var escalationPhrases = new ArrayList<>(List.of(
                "speak to a person",
                "talk to a human",
                "human agent",
                "representative"
        ));
        if (urgent) {
            escalationPhrases.add("this is urgent");
            escalationPhrases.add("emergency");
        }
        String tools = booking
                ? "Use the calendar availability tool before offering times and the booking tool only after confirmation."
                : "Use only approved knowledge and configured tools.";
        String prompt = """
                You are {{agent_name}}, the professional %s voice agent for {{business_name}}.

                ## Your Role
                Your business objective is: %s
                Stay within this scope, never invent business facts, and escalate requests you cannot safely complete.

                ## Business Information
                - Business name: {{business_name}}
                - Operating hours: {{business_hours}} ({{timezone}})
                - Services: {{services}}

                ## Conversation Flow
                1. Greet the caller and identify their goal.
                2. Ask one clear question at a time.
                3. Collect only the information required to complete the request.
                4. For bookings, collect service or reason, full name, date, time preference, then contact detail.
                5. Read important details back before taking an action.
                6. Summarize the outcome before ending the call.

                ## Sensitive Information
                Do not ask for date of birth, medical history, insurance, symptoms, or other sensitive details unless the business explicitly requires them.

                ## Tool Rules
                %s

                ## Safety and Escalation
                Never invent facts, availability, prices, policies, or completed actions.
                Transfer to a human when the request is urgent, sensitive, outside scope, or explicitly requested.

                ## Tone
                Be warm, concise, and professional. Respond in the caller's supported language.
                """.formatted(role, normalized, tools);
        return new GeneratedAgentDraftResponse(
                name,
                normalized.length() <= 500 ? normalized : normalized.substring(0, 500),
                openingDirection(booking),
                prompt.trim(),
                booking,
                "sw",
                List.of("sw", "en"),
                List.copyOf(escalationPhrases),
                List.of(
                        new GeneratedVariable("business_name", "Business name", "Official business name", true),
                        new GeneratedVariable("business_hours", "Operating hours", "Regular opening and closing hours", true),
                        new GeneratedVariable("services", "Services", "Services the agent may discuss", true)
                )
        );
    }

    private GeneratedAgentDraftResponse validateGenerated(GeneratedAgentDraftResponse generated) {
        if (generated == null || blank(generated.name()).isBlank() || blank(generated.systemPrompt()).isBlank()
                || blank(generated.greetingMessage()).isBlank()) {
            throw new IllegalStateException("AI generated an incomplete agent draft");
        }
        var defaultLanguage = ALLOWED_LANGUAGES.contains(generated.defaultLanguage())
                ? generated.defaultLanguage()
                : "en";
        var supported = new LinkedHashSet<String>();
        supported.add(defaultLanguage);
        if (generated.supportedLanguages() != null) {
            generated.supportedLanguages().stream().filter(ALLOWED_LANGUAGES::contains).forEach(supported::add);
        }
        var variableDefinitions = generated.variables() == null ? List.<GeneratedVariable>of() : generated.variables();
        var variableMap = variableDefinitions.stream()
                .filter(variable -> variable != null && VARIABLE_KEY.matcher(blank(variable.key())).matches())
                .collect(java.util.stream.Collectors.toMap(
                        GeneratedVariable::key,
                        variable -> new GeneratedVariable(
                                variable.key(),
                                blank(variable.label()).isBlank() ? humanize(variable.key()) : variable.label().trim(),
                                blank(variable.description()),
                                variable.required()
                        ),
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ));
        var referencedKeys = new LinkedHashSet<String>();
        var matcher = PLACEHOLDER.matcher(generated.systemPrompt());
        while (matcher.find()) {
            var key = matcher.group(1);
            if (!"agent_name".equals(key) && !"timezone".equals(key)) referencedKeys.add(key);
        }
        var variables = referencedKeys.stream()
                .limit(20)
                .map(key -> variableMap.getOrDefault(
                        key,
                        new GeneratedVariable(
                                key,
                                humanize(key),
                                "Value used by the agent when discussing " + key.replace('_', ' '),
                                true
                        )
                ))
                .toList();
        var phrases = generated.escalationPhrases() == null || generated.escalationPhrases().isEmpty()
                ? List.of("speak to a person", "talk to a human", "representative")
                : generated.escalationPhrases().stream().filter(phrase -> phrase != null && !phrase.isBlank())
                    .map(String::trim).distinct().limit(20).toList();
        return new GeneratedAgentDraftResponse(
                generated.name().trim(),
                blank(generated.description()),
                generated.greetingMessage().trim(),
                generated.systemPrompt().trim(),
                generated.bookingEnabled(),
                defaultLanguage,
                List.copyOf(supported),
                phrases,
                variables
        );
    }

    private String openingDirection(boolean booking) {
        return """
                Open naturally in the caller's language.
                Sound warm, concise, and professional.
                Mention {{agent_name}} only if it sounds natural.
                %s
                Ask one simple opening question and then wait.
                """.formatted(booking
                ? "If appointment booking is relevant, invite the caller to say what they would like to book."
                : "Invite the caller to say what they need.");
    }

    private ChatModel createGeminiModel(String apiKey, String model) {
        return GoogleGenAiChatModel.builder()
                .genAiClient(Client.builder().apiKey(apiKey).build())
                .defaultOptions(GoogleGenAiChatOptions.builder()
                        .model(model)
                        .maxOutputTokens(4096)
                        .thinkingBudget(GEMINI_THINKING_BUDGET)
                        .build())
                .build();
    }

    private String stripCodeFence(String content) {
        var trimmed = content.trim();
        if (!trimmed.startsWith("```")) return trimmed;
        var firstLineEnd = trimmed.indexOf('\n');
        var lastFence = trimmed.lastIndexOf("```");
        return firstLineEnd >= 0 && lastFence > firstLineEnd
                ? trimmed.substring(firstLineEnd + 1, lastFence).trim()
                : trimmed;
    }

    private String humanize(String key) {
        var words = key.replace('_', ' ');
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    private String blank(String value) {
        return value == null ? "" : value.trim();
    }

    private void sign(HttpRequest.Builder request, String body) {
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        request.header("X-Sauti-Timestamp", timestamp)
                .header("X-Sauti-Signature", "sha256=" + hmac(timestamp + "." + body));
    }

    private String hmac(String payload) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not sign agent draft AI webhook request", exception);
        }
    }

    private boolean containsAny(String value, String... terms) {
        for (String term : terms) {
            if (value.contains(term)) return true;
        }
        return false;
    }
}
