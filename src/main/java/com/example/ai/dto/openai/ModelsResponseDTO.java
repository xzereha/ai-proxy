package com.example.ai.dto.openai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ModelsResponseDTO(List<Model> data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static record Model(
            String id, String object, String created, @JsonProperty("owned_by") String ownedBy) {}
}
