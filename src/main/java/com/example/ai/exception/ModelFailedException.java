package com.example.ai.exception;

public class ModelFailedException extends RuntimeException {
    public ModelFailedException(String message) {
        super("Model request failed: " + message);
    }
}
