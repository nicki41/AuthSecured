package io.github.touchfalls.authsecured.domain.auth;

public enum AuthState {
    CONNECTED,
    AUTH_REQUIRED,
    AUTHENTICATED,
    KICKED,
    LOCKED
}
