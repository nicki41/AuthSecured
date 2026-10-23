package io.github.touchfalls.authsecured.domain.auth;

public enum AuthState {
    CONNECTED,
    AUTH_REQUIRED,
    /** Temporary password accepted; only a password change may complete authentication. */
    PASSWORD_CHANGE_REQUIRED,
    AUTHENTICATED,
    KICKED,
    LOCKED
}
