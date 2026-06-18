package com.example.ai.client;

import com.example.ai.config.OpenAiConfig;
import com.example.ai.dto.openai.ChatRequestDTO;
import com.example.ai.dto.openai.ChatRequestDTO.Format;
import com.example.ai.dto.openai.ChatRequestDTO.Input;
import com.example.ai.dto.openai.ChatRequestDTO.Text;
import com.example.ai.dto.openai.ModelsResponseDTO;
import com.example.ai.dto.openai.OpenAIResponse;
import com.example.ai.dto.v1.MessageResponseDTO;
import com.example.ai.exception.ModelFailedException;
import com.example.ai.exception.ModelIncompleteException;
import com.example.ai.exception.ModelRefusalException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.validation.Validator;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;

@Slf4j
@Component
public class OpenAIClient {
    private static final String BASE_URL = "https://api.openai.com/v1/";

    private final OpenAiConfig openAiConfig;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final int retryMaxAttempts;
    private final Duration retryInitialDelay;
    private final Duration retryMaxDelay;
    private final String fallbackMessage;
    private final int maxResponseLength;

    public OpenAIClient(
            OpenAiConfig openAiConfig,
            WebClient webClient,
            ObjectMapper objectMapper,
            Validator validator,
            @Value("${openai.retry.max-attempts}") int retryMaxAttempts,
            @Value("${openai.retry.initial-delay-ms}") long retryInitialDelayMs,
            @Value("${openai.retry.max-delay-ms}") long retryMaxDelayMs,
            @Value("${openai.response.fallback-message}") String fallbackMessage,
            @Value("${openai.response.max-length}") int maxResponseLength) {
        this.openAiConfig = openAiConfig;
        this.webClient = webClient;
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.retryMaxAttempts = retryMaxAttempts;
        this.retryInitialDelay = Duration.ofMillis(retryInitialDelayMs);
        this.retryMaxDelay = Duration.ofMillis(retryMaxDelayMs);
        this.fallbackMessage = fallbackMessage;
        this.maxResponseLength = maxResponseLength;
    }

    public Mono<ModelsResponseDTO> getAvailableModels() {
        return webClient
                .get()
                .uri(BASE_URL + "models")
                .header("Authorization", "Bearer " + openAiConfig.getApiKey())
                .retrieve()
                .bodyToMono(ModelsResponseDTO.class)
                .retryWhen(buildRetry());
    }

    public Mono<MessageResponseDTO> getResponse(String model, String prompt) {
        var body = buildRequest(model, prompt);
        return webClient
                .post()
                .uri(BASE_URL + "responses")
                .header("Authorization", "Bearer " + openAiConfig.getApiKey())
                .bodyValue(body)
                .retrieve()
                .bodyToMono(OpenAIResponse.class)
                .map(
                        response -> {
                            var text =
                                    switch (response.status()) {
                                        case "completed" -> {
                                            var refusal = response.extractRefusal();
                                            if (refusal != null) {
                                                throw new ModelRefusalException(refusal);
                                            }
                                            yield response.extractText();
                                        }
                                        case "failed" ->
                                                throw new ModelFailedException(response.status());
                                        case "in_progress" ->
                                                throw new IllegalStateException(
                                                        "Unexpected in_progress status in"
                                                                + " non-streaming response");
                                        case "cancelled" ->
                                                throw new IllegalStateException(
                                                        "Unexpected cancelled status in"
                                                                + " non-streaming response");
                                        case "incomplete" ->
                                                throw new ModelIncompleteException(
                                                        response.status());
                                        default ->
                                                throw new IllegalStateException(
                                                        "Unknown response status: "
                                                                + response.status());
                                    };
                            return parseAndValidate(text);
                        })
                .retryWhen(buildRetry());
    }

    private MessageResponseDTO parseAndValidate(String text) {
        MessageResponseDTO dto;
        try {
            dto = objectMapper.readValue(text, MessageResponseDTO.class);
        } catch (Exception e) {
            log.warn("Failed to parse model response, using fallback: {}", e.getMessage());
            return new MessageResponseDTO(fallbackMessage);
        }
        var violations = validator.validate(dto);
        if (!violations.isEmpty()) {
            log.warn("Model response failed validation ({}), using fallback", violations);
            return new MessageResponseDTO(fallbackMessage);
        }
        if (dto.response() != null && dto.response().length() > maxResponseLength) {
            log.warn(
                    "Model response exceeds max length ({} > {}), using fallback",
                    dto.response().length(),
                    maxResponseLength);
            return new MessageResponseDTO(fallbackMessage);
        }
        return dto;
    }

    private Retry buildRetry() {
        return Retry.backoff(retryMaxAttempts, retryInitialDelay)
                .maxBackoff(retryMaxDelay)
                .filter(throwable -> {
                    if (throwable instanceof WebClientResponseException e
                            && e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                        log.warn("Rate limited (429), retrying...");
                        return true;
                    }
                    return false;
                });
    }

    private ChatRequestDTO buildRequest(String model, String prompt) {
        return ChatRequestDTO.builder()
                .model(model)
                .temperature(0.1)
                .input(
                        List.of(
                                Input.builder()
                                        .role("system")
                                        .content(
                                                """
                                                    You are a chatbot.
                                                    You are going to respond to the user's question in a way that is helpful, concise, and informative.
                                                    Keep your responses short and to the point.
                                                    Your answers should be based on the user's question and not your own opinions or beliefs.
                                                    Your responses should be written in a way that is easy to understand for the user.
                                                    Do NOT include markdown formatting (like ```json), code fences, or conversational text. Return ONLY the raw JSON object.
                                                    Your response should be formatted as a JSON object following the specified schema.
                                                """)
                                        .build(),
                                Input.builder().role("user").content(prompt).build()))
                .text(
                        Text.builder()
                                .format(
                                        Format.builder()
                                                .type("json_schema")
                                                .strict(true)
                                                .name("response")
                                                .schema(
                                                        """
                                                        {
                                                          "type": "object",
                                                          "properties": {
                                                            "response": {
                                                              "type": "string"
                                                            }
                                                          },
                                                          "required": ["response"],
                                                          "additionalProperties": false
                                                        }
                                                        """)
                                                .build())
                                .build())
                .build();
    }
}
