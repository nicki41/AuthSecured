package com.example.authsecured.infrastructure.security;

import java.security.SecureRandom;

public final class SecureRandomProvider {
    private static final SecureRandom RANDOM = new SecureRandom();
    // Only unambiguous letters and digits are used. Minecraft command parsing can
    // treat punctuation differently across clients, proxies and server versions.
    private static final char[] TEMPORARY_PASSWORD_ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789".toCharArray();

    private SecureRandomProvider() {}

    public static byte[] generateSalt(int length) {
        byte[] salt = new byte[length];
        RANDOM.nextBytes(salt);
        return salt;
    }

    /** Generates a copy-safe temporary password without ambiguous characters. */
    public static char[] generateTemporaryPassword(int length) {
        if (length < 12 || length > 64) {
            throw new IllegalArgumentException("Temporary password length must be between 12 and 64");
        }
        char[] result = new char[length];
        for (int index = 0; index < result.length; index++) {
            result[index] = TEMPORARY_PASSWORD_ALPHABET[
                    RANDOM.nextInt(TEMPORARY_PASSWORD_ALPHABET.length)];
        }
        return result;
    }
}
