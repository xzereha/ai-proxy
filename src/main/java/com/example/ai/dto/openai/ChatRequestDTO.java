package com.example.ai.dto.openai;

import com.fasterxml.jackson.annotation.JsonRawValue;

import lombok.Builder;

import java.util.List;

@Builder
public record ChatRequestDTO(String model, List<Input> input, Text text, double temperature) {
    @Builder
    public static record Input(String role, String content) {}

    @Builder
    public static record Text(Format format) {}

    @Builder
    public static record Format(String type, String name, boolean strict, @JsonRawValue String schema) {}
}
