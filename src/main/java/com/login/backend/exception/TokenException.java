package com.login.backend.exception;

public class TokenException extends RuntimeException {
    public TokenException(String message) {
        super(message);
    }
}