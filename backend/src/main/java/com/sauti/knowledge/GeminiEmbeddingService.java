package com.sauti.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class GeminiEmbeddingService {
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String apiKey;
    private final String model;
    private final int dimensions;

    public GeminiEmbeddingService(
            ObjectMapper objectMapper,
            @Value("${spring.ai.google.genai.api-key:}") String apiKey,
            @Value("${sauti.rag.embedding-model:gemini-embedding-2}") String model,
            @Value("${sauti.rag.embedding-dimensions:768}") int dimensions
    ) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
        this.dimensions = dimensions;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    }

    public List<Float> embedDocument(String text) {
        return embed("Represent this business document for semantic retrieval:\n" + text);
    }

    public List<Float> embedQuery(String text) {
        return embed("Retrieve business information that answers this caller request:\n" + text);
    }

    private List<Float> embed(String text) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("GOOGLE_AI_API_KEY is required to index and search knowledge documents");
        }
        try {
            var body = objectMapper.createObjectNode();
            body.putObject("content").putArray("parts").addObject().put("text", text);
            body.put("output_dimensionality", dimensions);
            var request = HttpRequest.newBuilder(URI.create(
                            "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":embedContent"))
                    .timeout(Duration.ofSeconds(30))
                    .header("x-goog-api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Gemini embedding request failed with HTTP " + response.statusCode());
            }
            var root = objectMapper.readTree(response.body());
            var values = root.path("embedding").path("values");
            if (!values.isArray()) values = root.path("embeddings").path(0).path("values");
            if (!values.isArray() || values.size() != dimensions) {
                throw new IllegalStateException("Gemini returned an unexpected embedding dimension");
            }
            var result = new ArrayList<Float>(dimensions);
            values.forEach(value -> result.add((float) value.asDouble()));
            return normalizeIfNeeded(result);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Gemini embedding request was interrupted", exception);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Could not generate document embedding", exception);
        }
    }

    private List<Float> normalizeIfNeeded(List<Float> values) {
        if (!"gemini-embedding-001".equals(model) || dimensions == 3072) return List.copyOf(values);
        double magnitude = Math.sqrt(values.stream().mapToDouble(value -> value * value).sum());
        if (magnitude == 0) return List.copyOf(values);
        return values.stream().map(value -> (float) (value / magnitude)).toList();
    }

    public String vectorLiteral(List<Float> values) {
        return values.toString().replace(" ", "");
    }
}
