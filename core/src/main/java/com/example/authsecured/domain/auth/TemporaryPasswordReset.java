package com.example.authsecured.domain.auth;

import java.time.Instant;
import java.util.Arrays;

/** Secret-bearing result. Callers must erase the password once it has been delivered. */
public final class TemporaryPasswordReset {
    private final char[] password;
    private final Instant expiresAt;

    public TemporaryPasswordReset(char[] password, Instant expiresAt) {
        this.password = password == null ? new char[0] : password.clone();
        this.expiresAt = expiresAt;
    }

    public char[] copyPassword() { return password.clone(); }
    public Instant getExpiresAt() { return expiresAt; }

    public void destroy() { Arrays.fill(password, '\0'); }
}
