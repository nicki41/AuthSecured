package com.example.authsecured;

import com.example.authsecured.infrastructure.config.LocalizationManager;
import com.example.authsecured.domain.security.SecurityEventType;
import com.example.authsecured.ports.PlatformLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class LocalizationManagerTest {

    private static final List<String> BUNDLED_LANGUAGES = List.of("en", "ru", "es", "it", "fr");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-z]+)}");

    @TempDir
    File tempFolder;

    private LocalizationManager localizationManager;
    private static final PlatformLogger LOGGER = new PlatformLogger() {
        @Override public void info(String message) {}
        @Override public void warning(String message) {}
        @Override public void severe(String message) {}
        @Override public void severe(String message, Throwable throwable) {}
    };

    @BeforeEach
    void setUp() {
        localizationManager = new LocalizationManager(tempFolder, "en", LOGGER);
    }

    @Test
    void testDefaultEnglishLocalization() {
        localizationManager.init("en");
        assertEquals("en", localizationManager.getCurrentLanguage());

        String loginPrompt = localizationManager.getMessage("messages.login-prompt", "Default Prompt");
        assertTrue(loginPrompt.contains("§"), "Color code should be translated to section symbol");
        assertTrue(loginPrompt.contains("/login"));
    }

    @Test
    void testRussianLocalization() {
        localizationManager.init("ru");
        assertEquals("ru", localizationManager.getCurrentLanguage());

        String loginPrompt = localizationManager.getMessage("messages.login-prompt", "Default");
        assertTrue(loginPrompt.contains("войдите"), "Russian translation should contain Russian text: " + loginPrompt);
        assertTrue(localizationManager.getMessage("messages.auth-status-authenticated", "Default")
                .contains("вы авторизованы"));
        assertTrue(localizationManager.getMessage("messages.auth-status-required", "Default")
                .contains("требуется вход"));
        assertTrue(localizationManager.getMessage("messages.admin-status-title", "Default")
                .contains("Состояние"));
    }

    @Test
    void testSpanishLocalization() {
        localizationManager.init("es");
        assertEquals("es", localizationManager.getCurrentLanguage());

        String loginPrompt = localizationManager.getMessage("messages.login-prompt", "Default");
        assertTrue(loginPrompt.contains("inicia"), "Spanish translation should contain Spanish text: " + loginPrompt);
    }

    @Test
    void testItalianLocalization() {
        localizationManager.init("it");
        assertEquals("it", localizationManager.getCurrentLanguage());

        String loginPrompt = localizationManager.getMessage("messages.login-prompt", "Default");
        assertTrue(loginPrompt.contains("login"), "Italian translation should contain Italian text: " + loginPrompt);
    }

    @Test
    void testFrenchLocalization() {
        localizationManager.init("fr");
        assertEquals("fr", localizationManager.getCurrentLanguage());

        String loginPrompt = localizationManager.getMessage("messages.login-prompt", "Default");
        assertTrue(loginPrompt.contains("connecter"), "French translation should contain French text: " + loginPrompt);
    }

    @Test
    void testFallbackToEnglish() {
        localizationManager.init("ru");
        String missingKey = localizationManager.getMessage("messages.nonexistent-key", "Fallback Default");
        assertEquals("Fallback Default", missingKey);
    }

    @Test
    void testPlaceholderSubstitution() {
        localizationManager.init("en");
        String msg = localizationManager.getMessage("messages.login-usage", "&cUsage: /login <password>", Map.of("user", "Player1"));
        assertNotNull(msg);
    }

    @Test
    void missingKeysInExistingLanguageFileUseBundledTranslationForThatLanguage() throws Exception {
        File russianFile = new File(tempFolder, "lang/messages_ru.yml");
        Files.writeString(russianFile.toPath(), "messages:\n  login-prompt: '&eСтарый пользовательский перевод'\n");

        localizationManager.init("ru");

        assertTrue(localizationManager.getMessage("messages.login-prompt", "Default")
                .contains("Старый пользовательский перевод"));
        assertTrue(localizationManager.getMessage("messages.auth-status-authenticated", "Default")
                .contains("вы авторизованы"));
    }

    @Test
    void invalidExistingLanguageFileUsesBundledTranslationForThatLanguage() throws Exception {
        File russianFile = new File(tempFolder, "lang/messages_ru.yml");
        Files.writeString(russianFile.toPath(), "messages: [\n");

        localizationManager.init("ru");

        assertTrue(localizationManager.getMessage("messages.auth-status-required", "Default")
                .contains("требуется вход"));
    }

    @Test
    void allBundledLanguagesHaveTheSameKeysAndPlaceholders() {
        Map<String, String> english = loadBundledLanguage("en");
        assertFalse(english.isEmpty());

        for (String language : BUNDLED_LANGUAGES) {
            Map<String, String> translated = loadBundledLanguage(language);
            assertEquals(english.keySet(), translated.keySet(),
                    () -> "Localization keys differ for language " + language);
            translated.forEach((key, value) -> {
                assertFalse(value.isBlank(), () -> language + " has a blank value for " + key);
                assertEquals(placeholders(english.get(key)), placeholders(value),
                        () -> "Placeholders differ for " + language + " key " + key);
            });
        }
    }

    @Test
    void auditHasTranslationsForEveryGeneratedCategoryReasonAndSecurityEvent() {
        Map<String, String> english = loadBundledLanguage("en");
        List<String> requiredSuffixes = new java.util.ArrayList<>(List.of(
                "category-login", "category-security", "event-login-attempt",
                "event-ip-rate-limited", "event-account-rate-limited",
                "event-invalid-credentials", "event-account-locked",
                "event-temporary-password-expired", "event-invalid-password",
                "event-temporary-password-accepted", "event-success"));
        for (SecurityEventType eventType : SecurityEventType.values()) {
            requiredSuffixes.add("event-" + eventType.name().toLowerCase(java.util.Locale.ROOT)
                    .replace('_', '-'));
        }

        for (String suffix : requiredSuffixes) {
            assertTrue(english.containsKey("admin-audit-" + suffix),
                    () -> "Missing audit translation: admin-audit-" + suffix);
        }
    }

    private static Map<String, String> loadBundledLanguage(String language) {
        String resource = "lang/messages_" + language + ".yml";
        try (InputStream input = LocalizationManagerTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input, "Missing bundled localization " + resource);
            Object loaded = new Yaml(new SafeConstructor(new LoaderOptions())).load(input);
            assertInstanceOf(Map.class, loaded, "Invalid YAML root in " + resource);
            Object messages = ((Map<?, ?>) loaded).get("messages");
            assertInstanceOf(Map.class, messages, "Missing messages map in " + resource);
            Map<String, String> result = new LinkedHashMap<>();
            ((Map<?, ?>) messages).forEach((key, value) -> {
                assertInstanceOf(String.class, key, "Non-string key in " + resource);
                assertInstanceOf(String.class, value, "Non-string value for " + key + " in " + resource);
                result.put((String) key, (String) value);
            });
            return result;
        } catch (java.io.IOException exception) {
            throw new AssertionError("Could not read " + resource, exception);
        }
    }

    private static Set<String> placeholders(String value) {
        java.util.HashSet<String> result = new java.util.HashSet<>();
        Matcher matcher = PLACEHOLDER.matcher(value);
        while (matcher.find()) result.add(matcher.group(1));
        return Set.copyOf(result);
    }
}
