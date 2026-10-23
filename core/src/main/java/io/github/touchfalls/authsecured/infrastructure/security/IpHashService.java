package io.github.touchfalls.authsecured.infrastructure.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.atomic.AtomicBoolean;
import io.github.touchfalls.authsecured.ports.PlatformLogger;

public class IpHashService {

    private final byte[] secretKey;
    private final PlatformLogger logger;
    private final AtomicBoolean missingAddressReported = new AtomicBoolean();

    public IpHashService(String secret) {
        this(secret, null);
    }

    public IpHashService(String secret, PlatformLogger logger) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("A private IP hashing secret is required");
        }
        this.secretKey = secret.getBytes(StandardCharsets.UTF_8);
        this.logger = logger;
    }

    public byte[] hashIp(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            if (logger != null && missingAddressReported.compareAndSet(false, true)) {
                logger.severe("Player IP address is unavailable. Check proxy forwarding; IP-bound sessions are disabled for affected players.");
            }
            return null;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(secretKey, "HmacSHA256");
            mac.init(keySpec);
            return mac.doFinal(ipAddress.trim().getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to compute HMAC-SHA256 for IP address", e);
        }
    }

    public String hashIpToString(String ipAddress) {
        byte[] hash = hashIp(ipAddress);
        if (hash == null) return null;
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
