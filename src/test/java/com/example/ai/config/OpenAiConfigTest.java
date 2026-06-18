package com.example.ai.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class OpenAiConfigTest {

    @Test
    void validateApiKey_shouldThrowWhenKeyIsNull() {
        var config = new OpenAiConfig();
        ReflectionTestUtils.setField(config, "apiKey", null);
        assertThrows(IllegalStateException.class, config::validateApiKey);
    }

    @Test
    void validateApiKey_shouldThrowWhenKeyIsBlank() {
        var config = new OpenAiConfig();
        ReflectionTestUtils.setField(config, "apiKey", "   ");
        assertThrows(IllegalStateException.class, config::validateApiKey);
    }

    @Test
    void validateApiKey_shouldPassWhenKeyIsPresent() {
        var config = new OpenAiConfig();
        ReflectionTestUtils.setField(config, "apiKey", "sk-valid-key");
        assertDoesNotThrow(config::validateApiKey);
    }
}
