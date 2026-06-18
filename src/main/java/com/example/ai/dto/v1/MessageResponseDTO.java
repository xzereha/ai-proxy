package com.example.ai.dto.v1;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MessageResponseDTO(
        @NotNull @NotBlank String response) {}
