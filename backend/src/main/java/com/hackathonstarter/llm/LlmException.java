package com.hackathonstarter.llm;

import org.springframework.http.HttpStatus;

public class LlmException extends RuntimeException {

    private final HttpStatus status;

    public LlmException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public LlmException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
