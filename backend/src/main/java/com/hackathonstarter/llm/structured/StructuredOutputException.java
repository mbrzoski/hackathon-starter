package com.hackathonstarter.llm.structured;

import java.util.List;

public class StructuredOutputException extends RuntimeException {

    private final List<String> errors;

    public StructuredOutputException(String message, List<String> errors) {
        super(message);
        this.errors = errors;
    }

    public List<String> getErrors() {
        return errors;
    }
}
