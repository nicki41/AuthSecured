package io.github.touchfalls.authsecured.domain.auth;

public enum AuthState {
    AUTH_REQUIRED,
    /** Temporary password accepted; only a password change may complete authentication. */
    PASSWORD_CHANGE_REQUIRED,
    AUTHENTICATED
}
