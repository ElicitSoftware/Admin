package com.elicitsoftware.exception;

public class TokenGenerationError extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public TokenGenerationError(String message) {
        super(message);
    }
}
