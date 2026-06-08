package com.example.ai.dto.v1;

import jakarta.validation.constraints.NotBlank;

import org.springframework.validation.annotation.Validated;

@Validated
public record MessageRequestDTO(@NotBlank String message) {}
