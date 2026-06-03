package com.example.ai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/** OpenAI configuration. */
@Configuration
public class OpenAiConfig {

    @Value("${openai.key}")
    private String apiKey;

    public String getApiKey() {
        return apiKey;
    }
}
