package com.example.authsecured.application;

import com.example.authsecured.domain.auth.AuthResult;
import com.example.authsecured.domain.auth.AuthState;
import com.example.authsecured.infrastructure.config.LocalizationManager;
import com.example.authsecured.infrastructure.security.IpHashService;
import com.example.authsecured.ports.AuthPlatform;
import com.example.authsecured.ports.UnifiedCommandExecutor;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Unified implementation of command processing logic across all platforms.
 */
public class UnifiedCommandServiceImpl implements UnifiedCommandExecutor {

    private final AuthService authService;
    private final AuthPlatform platform;
    private final LocalizationManager localizationManager;
    private final IpHashService ipHashService;

    public UnifiedCommandServiceImpl(AuthService authService, AuthPlatform platform, LocalizationManager localizationManager, IpHashService ipHashService) {
        this.authService = authService;
        this.platform = platform;
        this.localizationManager = localizationManager;
        this.ipHashService = ipHashService;
    }

    @Override
    public CompletableFuture<Void> executeRegister(UUID playerUuid, String playerName, char[] password, char[] confirmPassword, String ipAddress) {
        if (!Arrays.equals(password, confirmPassword)) {
            platform.runTaskOnMainThread(() -> platform.sendMessage(playerUuid,
                    localizationManager.getMessage("messages.passwords-dont-match", "§cPasswords do not match!")));
            if (password != null) Arrays.fill(password, ' ');
            if (confirmPassword != null) Arrays.fill(confirmPassword, ' ');
            return CompletableFuture.completedFuture(null);
        }
        byte[] ipHash = ipHashService.hashIp(ipAddress);
        return authService.register(playerUuid, playerName, password, ipHash)
                .thenAccept(result -> platform.runTaskOnMainThread(() -> {
                    if (result == AuthResult.SUCCESS) {
                        platform.getRestrictionAdapter().setPlayerAuthenticated(playerUuid);
                        platform.sendMessage(playerUuid, localizationManager.getMessage("messages.register-success"));
                    } else {
                        String msgKey = switch (result) {
                            case ALREADY_REGISTERED -> "messages.already-registered";
                            case VALIDATION_FAILED -> "messages.password-policy-failed";
                            case RATE_LIMITED -> "messages.rate-limited";
                            default -> "messages.error";
                        };
                        platform.sendMessage(playerUuid, localizationManager.getMessage(msgKey));
                    }
                }))
                .exceptionally(throwable -> {
                    platform.runTaskOnMainThread(() -> platform.sendMessage(playerUuid, localizationManager.getMessage("messages.error")));
                    return null;
                })
                .whenComplete((v, ex) -> {
                    if (password != null) Arrays.fill(password, ' ');
                    if (confirmPassword != null) Arrays.fill(confirmPassword, ' ');
                });
    }

    @Override
    public CompletableFuture<Void> executeLogin(UUID playerUuid, String playerName, char[] password, String ipAddress) {
        byte[] ipHash = ipHashService.hashIp(ipAddress);
        return authService.login(playerUuid, playerName, password, ipHash)
                .thenAccept(result -> platform.runTaskOnMainThread(() -> {
                    if (result == AuthResult.SUCCESS) {
                        platform.getRestrictionAdapter().setPlayerAuthenticated(playerUuid);
                        platform.sendMessage(playerUuid, localizationManager.getMessage("messages.login-success"));
                    } else {
                        String msgKey = switch (result) {
                            case ACCOUNT_NOT_FOUND, NOT_REGISTERED, INVALID_PASSWORD -> "messages.login-failed";
                            case ACCOUNT_LOCKED -> "messages.account-locked";
                            case RATE_LIMITED -> "messages.rate-limited";
                            default -> "messages.error";
                        };
                        platform.sendMessage(playerUuid, localizationManager.getMessage(msgKey));
                    }
                }))
                .exceptionally(throwable -> {
                    platform.runTaskOnMainThread(() -> platform.sendMessage(playerUuid, localizationManager.getMessage("messages.error")));
                    return null;
                })
                .whenComplete((v, ex) -> {
                    if (password != null) Arrays.fill(password, ' ');
                });
    }

    @Override
    public CompletableFuture<Void> executeChangePassword(UUID playerUuid, char[] oldPassword, char[] newPassword, String ipAddress) {
        byte[] ipHash = ipHashService.hashIp(ipAddress);
        return authService.changePassword(playerUuid, oldPassword, newPassword, ipHash)
                .thenAccept(result -> platform.runTaskOnMainThread(() -> {
                    if (result == AuthResult.SUCCESS) {
                        platform.sendMessage(playerUuid, localizationManager.getMessage("messages.changepass-success"));
                    } else {
                        String msgKey = switch (result) {
                            case INVALID_PASSWORD -> "messages.old-password-invalid";
                            case VALIDATION_FAILED -> "messages.password-policy-failed";
                            default -> "messages.error";
                        };
                        platform.sendMessage(playerUuid, localizationManager.getMessage(msgKey));
                    }
                }))
                .exceptionally(throwable -> {
                    platform.runTaskOnMainThread(() -> platform.sendMessage(playerUuid, localizationManager.getMessage("messages.error")));
                    return null;
                })
                .whenComplete((v, ex) -> {
                    if (oldPassword != null) Arrays.fill(oldPassword, ' ');
                    if (newPassword != null) Arrays.fill(newPassword, ' ');
                });
    }

    @Override
    public CompletableFuture<Void> executeLogout(UUID playerUuid) {
        return authService.getSessionService().revokeActiveSessionForPlayer(playerUuid)
                .whenComplete((ignored, throwable) -> platform.runTaskOnMainThread(() -> {
                    platform.getRestrictionAdapter().setPlayerUnauthenticated(playerUuid, null);
                    String key = throwable == null ? "messages.logged-out" : "messages.error";
                    platform.sendMessage(playerUuid, localizationManager.getMessage(key));
                }));
    }

    @Override
    public void executeAuthStatus(UUID playerUuid) {
        AuthState state = platform.getRestrictionAdapter().getAuthState(playerUuid);
        String message = state == AuthState.AUTHENTICATED
                ? "§aAuth Status: AUTHENTICATED"
                : "§cAuth Status: AUTH_REQUIRED";
        platform.sendMessage(playerUuid, message);
    }
}
