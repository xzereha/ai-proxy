package com.example.ai.exception;

public class ModelResponseParseException extends RuntimeException {
    public ModelResponseParseException(String message, Throwable cause) {
        super("Failed to parse model response: " + message, cause);
    }
}
