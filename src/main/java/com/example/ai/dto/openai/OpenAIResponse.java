package com.example.ai.dto.openai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenAIResponse(
        String id,
        String object,
        @JsonProperty("created_at") long createdAt,
        String status,
        String model,
        List<OutputItem> output,
        Usage usage) {

    public String extractText() {
        if (output == null || output.isEmpty()) {
            return null;
        }
        return output.getFirst().content().stream()
                .filter(c -> "output_text".equals(c.type()))
                .findFirst()
                .map(ContentItem::text)
                .orElse(null);
    }

    public String extractRefusal() {
        if (output == null || output.isEmpty()) {
            return null;
        }
        return output.getFirst().content().stream()
                .filter(c -> "refusal".equals(c.type()))
                .findFirst()
                .map(ContentItem::refusal)
                .orElse(null);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OutputItem(String id, String type, String role, List<ContentItem> content) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ContentItem(String type, String text, String refusal, List<Object> annotations) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Usage(
            @JsonProperty("input_tokens") int inputTokens,
            @JsonProperty("output_tokens") int outputTokens,
            @JsonProperty("output_tokens_details") OutputTokensDetails outputTokensDetails,
            @JsonProperty("total_tokens") int totalTokens) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record OutputTokensDetails(@JsonProperty("reasoning_tokens") int reasoningTokens) {}
    }
}
