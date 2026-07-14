package com.sauti.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "sauti.llm.provider", havingValue = "spring-ai")
public class SpringAiToolCallingLlmProvider implements LlmToolCallingProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpringAiToolCallingLlmProvider.class);
    // Voice turns need fast first-token latency. Do not enable Gemini thinking here.
    private static final int GEMINI_THINKING_BUDGET = 0;
    private static final int MAX_OUTPUT_TOKENS = 120;
    private static final double VOICE_TEMPERATURE = 0.45;

    private final ObjectMapper objectMapper;
    private final String primaryModel;
    private final String fallbackModel;
    private final ChatModel primaryChatModel;
    private final ChatModel fallbackChatModel;

    @Autowired
    public SpringAiToolCallingLlmProvider(
            ObjectMapper objectMapper,
            @Value("${sauti.llm.primary-model}") String primaryModel,
            @Value("${sauti.llm.fallback-model}") String fallbackModel,
            @Value("${spring.ai.google.genai.api-key:}") String googleApiKey,
            @Value("${spring.ai.openai.api-key:}") String openAiApiKey
    ) {
        this(
                objectMapper,
                requireModel(primaryModel, "SAUTI_LLM_PRIMARY_MODEL"),
                requireModel(fallbackModel, "SAUTI_LLM_FALLBACK_MODEL"),
                createOpenAiModel(
                        primaryModel,
                        requireApiKey(openAiApiKey, "OPENAI_API_KEY", primaryModel)
                ),
                createGeminiModel(
                        fallbackModel,
                        requireApiKey(googleApiKey, "GOOGLE_AI_API_KEY", fallbackModel)
                )
        );
    }

    SpringAiToolCallingLlmProvider(
            ObjectMapper objectMapper,
            String primaryModel,
            String fallbackModel,
            ChatModel primaryChatModel,
            ChatModel fallbackChatModel
    ) {
        this.objectMapper = objectMapper;
        this.primaryModel = primaryModel;
        this.fallbackModel = fallbackModel;
        this.primaryChatModel = primaryChatModel;
        this.fallbackChatModel = fallbackChatModel;
    }

    @Override
    public LlmToolTurnResponse completeTurn(LlmToolTurnContext context) {
        try {
            return request(context, primaryChatModel, primaryModel, ModelProvider.OPENAI);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Primary OpenAI LLM turn failed for callId={}; retrying with Gemini fallback",
                    context.callId(),
                    exception
            );
        }
        return request(context, fallbackChatModel, fallbackModel, ModelProvider.GOOGLE);
    }

    @Override
    public LlmToolTurnResponse streamTurn(LlmToolTurnContext context, Consumer<String> textDeltaConsumer) {
        var emittedPrimaryText = new java.util.concurrent.atomic.AtomicBoolean(false);
        try {
            return streamRequest(
                    context,
                    primaryChatModel,
                    primaryModel,
                    ModelProvider.OPENAI,
                    delta -> {
                        emittedPrimaryText.set(true);
                        textDeltaConsumer.accept(delta);
                    }
            );
        } catch (RuntimeException exception) {
            if (emittedPrimaryText.get()) {
                LOGGER.warn(
                        "Primary OpenAI stream failed after emitting audio text for callId={}; not replaying the turn with Gemini",
                        context.callId(),
                        exception
                );
                throw exception;
            }
            LOGGER.warn(
                    "Primary OpenAI stream failed before first text for callId={}; retrying with Gemini fallback",
                    context.callId(),
                    exception
            );
        }
        return streamRequest(
                context,
                fallbackChatModel,
                fallbackModel,
                ModelProvider.GOOGLE,
                textDeltaConsumer
        );
    }

    private LlmToolTurnResponse streamRequest(
            LlmToolTurnContext context,
            ChatModel chatModel,
            String modelName,
            ModelProvider provider,
            Consumer<String> textDeltaConsumer
    ) {
        var callbacks = context.tools().stream().map(this::toolCallback).toList();
        var prompt = new Prompt(messages(context), options(modelName, provider, callbacks));
        var text = new StringBuilder();
        var streamedToolCalls = new java.util.concurrent.atomic.AtomicReference<AssistantMessage>();
        chatModel.stream(prompt).toStream().forEach(response -> {
            var output = response.getResult() == null ? null : response.getResult().getOutput();
            var delta = output == null
                    ? ""
                    : output.getText();
            if (delta != null && !delta.isEmpty()) {
                var incremental = incrementalText(text, delta);
                if (!incremental.isEmpty()) textDeltaConsumer.accept(incremental);
            }
            if (output != null && output.getToolCalls() != null && !output.getToolCalls().isEmpty()) {
                streamedToolCalls.set(output);
            }
        });
        var toolOutput = streamedToolCalls.get();
        return new LlmToolTurnResponse(text.toString(), toolOutput == null ? List.of() : toolCalls(toolOutput));
    }

    static String incrementalText(StringBuilder accumulated, String incoming) {
        if (incoming == null || incoming.isEmpty()) return "";
        if (incoming.startsWith(accumulated.toString())) {
            var suffix = incoming.substring(accumulated.length());
            accumulated.setLength(0);
            accumulated.append(incoming);
            return suffix;
        }
        accumulated.append(incoming);
        return incoming;
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
        return new LlmToolTurnResponse(output.getText() == null ? "" : output.getText(), toolCalls(output));
    }

    List<LlmToolCall> toolCalls(AssistantMessage output) {
        var calls = output.getToolCalls();
        if (calls == null || calls.isEmpty()) {
            return List.of();
        }
        return calls.stream()
                .map(call -> new LlmToolCall(call.id(), call.name(), parseArguments(call.arguments())))
                .toList();
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
                .inputSchema(writeJson(sanitizeSchema(tool.inputSchema())))
                .build();
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return definition;
            }

            @Override
            public String call(String toolInput) {
                return writeJson(Map.of(
                        "error", "Tool execution is handled by Sauti after the model returns the tool call.",
                        "tool", tool.name()
                ));
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

    private static ChatModel createGeminiModel(String modelName, String apiKey) {
        if (!modelName.toLowerCase(java.util.Locale.ROOT).startsWith("gemini-")) {
            throw new IllegalArgumentException("Fallback LLM must be a Gemini model; received " + modelName);
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

    private static ChatModel createOpenAiModel(String modelName, String apiKey) {
        if (!modelName.toLowerCase(java.util.Locale.ROOT).startsWith("gpt-")) {
            throw new IllegalArgumentException("Primary LLM must be an OpenAI GPT model; received " + modelName);
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

    @SuppressWarnings("unchecked")
    Map<String, Object> sanitizeSchema(Map<String, Object> schema) {
        return (Map<String, Object>) sanitizeValue(schema);
    }

    private Object sanitizeValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            var sanitized = new LinkedHashMap<String, Object>();
            map.forEach((key, nestedValue) -> {
                if (key == null || "format".equals(key.toString())) {
                    return;
                }
                sanitized.put(key.toString(), sanitizeValue(nestedValue));
            });
            return sanitized;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::sanitizeValue).toList();
        }
        return value;
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
