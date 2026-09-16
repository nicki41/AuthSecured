package io.github.touchfalls.authsecured.ports;

public interface PasswordHasher {
    String hash(char[] password);
    boolean verify(char[] password, String hash);
    String getDummyHash();
}
