package com.example.ai.controller;

import com.example.ai.dto.openai.ModelsResponseDTO;
import com.example.ai.dto.v1.MessageRequestDTO;
import com.example.ai.dto.v1.MessageResponseDTO;
import com.example.ai.service.AiService;

import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;

@AllArgsConstructor
@RequestMapping("/api/v1")
@RestController
public class AiController {
    private final AiService aiService;

    @GetMapping(value = "/models", produces = "application/json")
    public Mono<ModelsResponseDTO> getAvailableModels() {
        return aiService.getAvailableModels();
    }

    @PostMapping(value = "/chat", produces = "application/json")
    public Mono<MessageResponseDTO> chat(@RequestBody @NotNull MessageRequestDTO request) {
        return aiService.chat(request.message());
    }
}
