package io.github.touchfalls.authsecured.ports;

public interface PasswordHasher {
    String hash(char[] password);
    boolean verify(char[] password, String hash);
    String getDummyHash();

    /**
     * True when a stored hash was produced with different cost settings than this hasher
     * now uses, so it should be replaced after the password has been proven.
     */
    default boolean needsRehash(String hash) {
        return false;
    }
}
