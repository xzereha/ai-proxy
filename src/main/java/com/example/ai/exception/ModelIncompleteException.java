package com.example.ai.exception;

public class ModelIncompleteException extends RuntimeException {
    public ModelIncompleteException(String reason) {
        super("Model response incomplete: " + reason);
    }
}
