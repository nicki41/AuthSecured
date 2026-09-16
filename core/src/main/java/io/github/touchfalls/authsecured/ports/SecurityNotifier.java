package io.github.touchfalls.authsecured.ports;

/** Sends privacy-safe operational alerts. Implementations must never include passwords or raw IP addresses. */
public interface SecurityNotifier {
    void loginFailure(String username, String reason);

    SecurityNotifier NOOP = (username, reason) -> { };
}
