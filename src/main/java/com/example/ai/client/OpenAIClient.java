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
import com.example.ai.exception.ModelResponseParseException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.AllArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

import java.util.List;

@Component
@AllArgsConstructor
public class OpenAIClient {
    private final OpenAiConfig openAiConfig;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private static final String BASE_URL = "https://api.openai.com/v1/";

    public Mono<ModelsResponseDTO> getAvailableModels() {
        return webClient
                .get()
                .uri(BASE_URL + "models")
                .header("Authorization", "Bearer " + openAiConfig.getApiKey())
                .retrieve()
                .bodyToMono(ModelsResponseDTO.class);
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
                                                    "Unexpected in_progress status in non-streaming"
                                                        + " response");
                                        case "cancelled" ->
                                            throw new IllegalStateException(
                                                    "Unexpected cancelled status in non-streaming"
                                                        + " response");
                                        case "incomplete" ->
                                            throw new ModelIncompleteException(response.status());
                                        default ->
                                            throw new IllegalStateException(
                                                    "Unknown response status: " + response.status());
                                    };
                            try {
                                return objectMapper.readValue(text, MessageResponseDTO.class);
                            } catch (Exception e) {
                                throw new ModelResponseParseException(text, e);
                            }
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
