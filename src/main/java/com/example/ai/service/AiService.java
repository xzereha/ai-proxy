package com.example.ai.service;

import com.example.ai.client.OpenAIClient;
import com.example.ai.dto.openai.ModelsResponseDTO;
import com.example.ai.dto.v1.MessageResponseDTO;

import lombok.AllArgsConstructor;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public class AiService {
    private final OpenAIClient client;

    @Cacheable("models")
    public Mono<ModelsResponseDTO> getAvailableModels() {
        return client.getAvailableModels();
    }

    public Mono<MessageResponseDTO> chat(String message) {
        return client.getResponse("gpt-4o", message);
    }
}
