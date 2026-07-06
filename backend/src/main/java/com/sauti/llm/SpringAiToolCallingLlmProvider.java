package com.sauti.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "sauti.llm.provider", havingValue = "spring-ai")
public class SpringAiToolCallingLlmProvider implements LlmToolCallingProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpringAiToolCallingLlmProvider.class);
    // Voice turns need fast first-token latency. Do not enable Gemini thinking here.
    private static final int GEMINI_THINKING_BUDGET = 0;
    private static final int MAX_OUTPUT_TOKENS = 384;
    private static final double VOICE_TEMPERATURE = 0.65;

    private final ObjectMapper objectMapper;
    private final String defaultModel;
    private final String advancedModel;
    private final ChatModel defaultChatModel;
    private final ChatModel advancedChatModel;

    public SpringAiToolCallingLlmProvider(
            ObjectMapper objectMapper,
            @Value("${sauti.llm.default-model}") String defaultModel,
            @Value("${sauti.llm.advanced-model}") String advancedModel,
            @Value("${spring.ai.google.genai.api-key:}") String googleApiKey,
            @Value("${spring.ai.openai.api-key:}") String openAiApiKey
    ) {
        this.objectMapper = objectMapper;
        this.defaultModel = requireModel(defaultModel, "SAUTI_LLM_DEFAULT_MODEL");
        this.advancedModel = requireModel(advancedModel, "SAUTI_LLM_ADVANCED_MODEL");
        this.defaultChatModel = createGeminiModel(
                this.defaultModel,
                requireApiKey(googleApiKey, "GOOGLE_AI_API_KEY", this.defaultModel)
        );
        this.advancedChatModel = openAiApiKey == null || openAiApiKey.isBlank()
                ? null
                : createOpenAiModel(this.advancedModel, openAiApiKey);
    }

    @Override
    public LlmToolTurnResponse completeTurn(LlmToolTurnContext context) {
        var advanced = "advanced".equalsIgnoreCase(context.agent().llmTier());
        if (advanced && advancedChatModel == null) {
            throw new IllegalStateException(
                    "OPENAI_API_KEY is required when an agent uses the Advanced LLM tier"
            );
        }
        return request(
                context,
                advanced ? advancedChatModel : defaultChatModel,
                advanced ? advancedModel : defaultModel,
                advanced ? ModelProvider.OPENAI : ModelProvider.GOOGLE
        );
    }

    private LlmToolTurnResponse request(
            LlmToolTurnContext context,
            ChatModel chatModel,
            String modelName,
            ModelProvider provider
    ) {
        var callbacks = context.tools().stream().map(this::toolCallback).toList();
        var response = chatModel.call(new Prompt(
                messages(context),
                options(modelName, provider, callbacks)
        ));
        var output = response.getResult().getOutput();
        var toolCalls = output.getToolCalls().stream()
                .map(call -> new LlmToolCall(call.id(), call.name(), parseArguments(call.arguments())))
                .toList();
        return new LlmToolTurnResponse(output.getText() == null ? "" : output.getText(), toolCalls);
    }

    private List<Message> messages(LlmToolTurnContext context) {
        var result = new ArrayList<Message>();
        result.add(new SystemMessage(context.systemPrompt()));
        for (var message : context.messages()) {
            switch (message.role()) {
                case "user" -> result.add(new UserMessage(message.content()));
                case "assistant" -> result.add(assistantMessage(message));
                case "tool" -> result.add(toolResponseMessage(message.toolResult()));
                default -> LOGGER.debug("Ignoring unsupported conversation role {}", message.role());
            }
        }
        return result;
    }

    private AssistantMessage assistantMessage(ConversationMessage message) {
        var toolCalls = message.toolCalls().stream()
                .map(call -> new AssistantMessage.ToolCall(
                        call.id(),
                        "function",
                        call.name(),
                        writeJson(call.arguments())
                ))
                .toList();
        return AssistantMessage.builder()
                .content(message.content() == null ? "" : message.content())
                .toolCalls(toolCalls)
                .build();
    }

    private ToolResponseMessage toolResponseMessage(LlmToolResult result) {
        if (result == null) {
            throw new IllegalArgumentException("Tool conversation message is missing its result");
        }
        var responseData = result.success()
                ? writeJson(result.result())
                : writeJson(Map.of("error", result.error()));
        return ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse(
                        result.toolCallId(),
                        result.name(),
                        responseData
                )))
                .build();
    }

    private ToolCallback toolCallback(LlmToolDefinition tool) {
        var definition = ToolDefinition.builder()
                .name(tool.name())
                .description(tool.description())
                .inputSchema(writeJson(tool.inputSchema()))
                .build();
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return definition;
            }

            @Override
            public String call(String toolInput) {
                throw new UnsupportedOperationException(
                        "Sauti executes tools through ToolFulfillmentRouter, not inside Spring AI"
                );
            }
        };
    }

    private ChatOptions options(String modelName, ModelProvider provider, List<ToolCallback> callbacks) {
        if (provider == ModelProvider.OPENAI) {
            return OpenAiChatOptions.builder()
                    .model(modelName)
                    .maxTokens(MAX_OUTPUT_TOKENS)
                    .temperature(VOICE_TEMPERATURE)
                    .toolCallbacks(callbacks)
                    .internalToolExecutionEnabled(false)
                    .build();
        }
        return GoogleGenAiChatOptions.builder()
                .model(modelName)
                .maxOutputTokens(MAX_OUTPUT_TOKENS)
                .temperature(VOICE_TEMPERATURE)
                .thinkingBudget(GEMINI_THINKING_BUDGET)
                .toolCallbacks(callbacks)
                .internalToolExecutionEnabled(false)
                .build();
    }

    private ChatModel createGeminiModel(String modelName, String apiKey) {
        if (!modelName.toLowerCase(java.util.Locale.ROOT).startsWith("gemini-")) {
            throw new IllegalArgumentException("Standard tier requires a Gemini model; received " + modelName);
        }
        return GoogleGenAiChatModel.builder()
                .genAiClient(Client.builder().apiKey(apiKey).build())
                .defaultOptions(GoogleGenAiChatOptions.builder()
                        .model(modelName)
                        .maxOutputTokens(MAX_OUTPUT_TOKENS)
                        .temperature(VOICE_TEMPERATURE)
                        .thinkingBudget(GEMINI_THINKING_BUDGET)
                        .build())
                .build();
    }

    private ChatModel createOpenAiModel(String modelName, String apiKey) {
        if (!modelName.toLowerCase(java.util.Locale.ROOT).startsWith("gpt-")) {
            throw new IllegalArgumentException("Advanced tier requires an OpenAI GPT model; received " + modelName);
        }
        return OpenAiChatModel.builder()
                .openAiApi(OpenAiApi.builder().apiKey(apiKey).build())
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(modelName)
                        .maxTokens(MAX_OUTPUT_TOKENS)
                        .temperature(VOICE_TEMPERATURE)
                        .build())
                .build();
    }

    private Map<String, Object> parseArguments(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            @SuppressWarnings("unchecked")
            var arguments = (Map<String, Object>) objectMapper.readValue(json, Map.class);
            return arguments;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("LLM returned invalid tool arguments", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize Spring AI tool data", exception);
        }
    }

    private static String requireModel(String value, String environmentVariable) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(environmentVariable + " must be configured when SAUTI_LLM_PROVIDER=spring-ai");
        }
        return value;
    }

    private static String requireApiKey(String value, String environmentVariable, String modelName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(environmentVariable + " is required to use model " + modelName);
        }
        return value;
    }

    private enum ModelProvider {
        GOOGLE,
        OPENAI
    }
}
