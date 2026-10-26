package io.github.touchfalls.authsecured.infrastructure.security;

import io.github.touchfalls.authsecured.ports.PasswordHasher;
import io.github.touchfalls.authsecured.ports.PlatformLogger;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Objects;
import java.util.Arrays;

public final class Argon2PasswordHasher implements PasswordHasher {

    private final int memoryKib;
    private final int iterations;
    private final int parallelism;
    private final int hashLength;
    private final int saltLength;
    private final String dummyHash;
    private final PlatformLogger logger;

    public Argon2PasswordHasher(int memoryKib, int iterations, int parallelism, int hashLength, int saltLength) {
        this(memoryKib, iterations, parallelism, hashLength, saltLength, null);
    }

    public Argon2PasswordHasher(int memoryKib, int iterations, int parallelism,
                                int hashLength, int saltLength, PlatformLogger logger) {
        this.memoryKib = memoryKib;
        this.iterations = iterations;
        this.parallelism = parallelism;
        this.hashLength = hashLength;
        this.saltLength = saltLength;
        this.logger = logger;
        this.dummyHash = hash("dummyPassword123".toCharArray());
    }

    public Argon2PasswordHasher() {
        this(65536, 3, 2, 32, 16);
    }

    @Override
    public String hash(char[] password) {
        Objects.requireNonNull(password, "Password cannot be null");
        byte[] salt = SecureRandomProvider.generateSalt(saltLength);
        byte[] hash = generateArgon2idHash(password, salt, memoryKib, iterations, parallelism, hashLength);

        Base64.Encoder encoder = Base64.getEncoder().withoutPadding();
        return String.format("$argon2id$v=19$m=%d,t=%d,p=%d$%s$%s",
                memoryKib, iterations, parallelism,
                encoder.encodeToString(salt),
                encoder.encodeToString(hash));
    }

    @Override
    public boolean verify(char[] password, String encodedHash) {
        if (password == null || encodedHash == null || !encodedHash.startsWith("$argon2id$")) {
            return false;
        }
        if (encodedHash.length() > 512) return malformed("excessive length");
        try {
            String[] parts = encodedHash.split("\\$", -1);
            if (parts.length != 6 || !parts[2].equals("v=19")) return malformed("invalid format or version");

            // parts[0] is empty
            // parts[1] = argon2id
            // parts[2] = v=19
            // parts[3] = m=65536,t=3,p=2
            // parts[4] = base64 Salt
            // parts[5] = base64 Hash

            String[] params = parts[3].split(",");
            if (params.length != 3 || !params[0].startsWith("m=")
                    || !params[1].startsWith("t=") || !params[2].startsWith("p=")) {
                return malformed("invalid cost format");
            }
            int m = Integer.parseInt(params[0].substring(2));
            int t = Integer.parseInt(params[1].substring(2));
            int p = Integer.parseInt(params[2].substring(2));
            if (m < 8 || m > 1_048_576 || t < 1 || t > 20 || p < 1 || p > 16) {
                return malformed("cost outside supported bounds");
            }
            if (parts[4].length() > 88 || parts[5].length() > 88) {
                return malformed("salt or hash length outside supported bounds");
            }

            Base64.Decoder decoder = Base64.getDecoder();
            byte[] salt = decoder.decode(parts[4]);
            byte[] expectedHash = decoder.decode(parts[5]);
            if (salt.length < 8 || salt.length > 64 || expectedHash.length < 16 || expectedHash.length > 64) {
                return malformed("salt or hash length outside supported bounds");
            }

            byte[] computedHash = generateArgon2idHash(password, salt, m, t, p, expectedHash.length);
            try {
                return MessageDigest.isEqual(computedHash, expectedHash);
            } finally {
                Arrays.fill(computedHash, (byte) 0);
            }
        } catch (IllegalArgumentException exception) {
            return malformed("invalid encoding or numeric cost");
        }
    }

    /**
     * A hash needs replacing when its memory, iteration, parallelism, salt or output size
     * differs from the configured values. Malformed hashes are left to {@link #verify}, which
     * rejects them.
     */
    @Override
    public boolean needsRehash(String encodedHash) {
        if (encodedHash == null || !encodedHash.startsWith("$argon2id$") || encodedHash.length() > 512) {
            return false;
        }
        String[] parts = encodedHash.split("\\$", -1);
        if (parts.length != 6 || !parts[2].equals("v=19")) return false;
        if (!parts[3].equals("m=" + memoryKib + ",t=" + iterations + ",p=" + parallelism)) return true;
        try {
            Base64.Decoder decoder = Base64.getDecoder();
            return decoder.decode(parts[4]).length != saltLength || decoder.decode(parts[5]).length != hashLength;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean malformed(String reason) {
        if (logger != null) logger.warning("Stored Argon2 hash rejected: " + reason + ". Hash details are hidden.");
        return false;
    }

    @Override
    public String getDummyHash() {
        return dummyHash;
    }

    private byte[] generateArgon2idHash(char[] password, byte[] salt, int memoryKib, int iterations, int parallelism, int hashLen) {
        byte[] passwordBytes = charToByteArray(password);
        try {
            Argon2Parameters.Builder builder = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                    .withMemoryAsKB(memoryKib)
                    .withIterations(iterations)
                    .withParallelism(parallelism)
                    .withSalt(salt);

            Argon2BytesGenerator generator = new Argon2BytesGenerator();
            generator.init(builder.build());

            byte[] result = new byte[hashLen];
            generator.generateBytes(passwordBytes, result, 0, result.length);
            return result;
        } finally {
            java.util.Arrays.fill(passwordBytes, (byte) 0);
        }
    }

    private byte[] charToByteArray(char[] chars) {
        ByteBuffer byteBuffer = StandardCharsets.UTF_8.encode(CharBuffer.wrap(chars));
        try {
            byte[] bytes = new byte[byteBuffer.remaining()];
            byteBuffer.get(bytes);
            return bytes;
        } finally {
            byteBuffer.clear();
            while (byteBuffer.hasRemaining()) byteBuffer.put((byte) 0);
        }
    }
}
