package com.example.ai.exception;

public class ModelRefusalException extends RuntimeException {
    private final String refusal;

    public ModelRefusalException(String refusal) {
        super("Model refused request: " + refusal);
        this.refusal = refusal;
    }

    public String getRefusal() {
        return refusal;
    }
}
