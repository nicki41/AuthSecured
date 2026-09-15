package com.example.authsecured.ports;

/** Raised when a database uniqueness constraint rejects a duplicate account. */
public final class DuplicateAccountException extends RuntimeException {
    public DuplicateAccountException(String message, Throwable cause) {
        super(message, cause);
    }
}
