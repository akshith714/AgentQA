package com.agentqa.llm;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);

    private final RestClient http;
    private final ObjectMapper mapper;

    public LlmClient(ObjectMapper mapper) {
        String apiKey = System.getenv("GROQ_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("GROQ_API_KEY environment variable is not set");
        }
        this.mapper = mapper;

        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(60_000);

        this.http = RestClient.builder()
                .requestFactory(factory)
                .baseUrl("https://api.groq.com")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    public String complete(String systemPrompt, String userPrompt) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                return callOnce(systemPrompt, userPrompt);
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() != 429) {
                    throw e;
                }
                last = e;
                if (!backOff(attempt, e)) {
                    throw e;
                }
            } catch (RuntimeException e) {
                last = e;
                if (!backOff(attempt, e)) {
                    throw e;
                }
            }
        }
        throw last;
    }

    private boolean backOff(int attempt, RuntimeException cause) {
        if (attempt == 3) {
            return false;
        }
        log.warn("LLM call failed (attempt {} of 3), retrying: {}", attempt, cause.getMessage());
        try {
            Thread.sleep(1000L * attempt);
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private String callOnce(String systemPrompt, String userPrompt) {
        Map<String, Object> body = Map.of(
                "model", "openai/gpt-oss-120b",
                "max_tokens", 2000,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt))
        );

        JsonNode response = http.post()
                .uri("/openai/v1/chat/completions")
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        return response.path("choices").path(0).path("message").path("content").asString();
    }

    public JsonNode parse(String reply) {
        if (reply == null) {
            return null;
        }
        int start = reply.indexOf('{');
        int end = reply.lastIndexOf('}');
        if (start < 0 || end <= start) {
            log.warn("Model reply contained no JSON object");
            return null;
        }
        try {
            return mapper.readTree(reply.substring(start, end + 1));
        } catch (JacksonException e) {
            log.warn("Model reply was not valid JSON: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Removes a markdown code fence around a reply. Every prompt forbids fences and
     * models still emit them, and one stray fence makes the sandbox build fail.
     */
    public static String stripFences(String reply) {
        if (reply == null) {
            return "";
        }
        String text = reply.strip();
        if (!text.startsWith("```")) {
            return text;
        }
        int firstLineEnd = text.indexOf('\n');
        if (firstLineEnd < 0) {
            return "";
        }
        text = text.substring(firstLineEnd + 1);
        int closing = text.lastIndexOf("```");
        return (closing < 0 ? text : text.substring(0, closing)).strip();
    }

    public String field(String reply, String key, String fallback) {
        JsonNode node = parse(reply);
        if (node == null) {
            return fallback;
        }
        String value = node.path(key).asString("");
        return value.isBlank() ? fallback : value;
    }
}
