package com.example.authsecured.infrastructure.config;

import com.example.authsecured.ports.PlatformLogger;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ConfigManager {

    private final File dataFolder;
    private final PlatformLogger logger;
    private final LocalizationManager localizationManager;
    private volatile Map<String, Object> configValues = Map.of();

    public ConfigManager(File dataFolder, PlatformLogger logger) {
        this.dataFolder = dataFolder;
        this.logger = logger;
        this.localizationManager = new LocalizationManager(dataFolder, "en", logger);
    }

    public void loadConfigurations() {
        File configFile = new File(dataFolder, "config.yml");
        try {
            Files.createDirectories(dataFolder.toPath());
            saveDefaultIfNotExists(configFile, "config.yml");

            Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
            Map<String, Object> loadedValues;
            try (InputStream input = new FileInputStream(configFile)) {
                Object loaded = yaml.load(input);
                if (loaded == null) {
                    loadedValues = Map.of();
                } else if (loaded instanceof Map<?, ?> root) {
                    loadedValues = flatten(root);
                } else {
                    throw new IllegalArgumentException("the YAML root must be a mapping");
                }
            }

            // Publish a complete, immutable snapshot so event threads never see a
            // partially reloaded configuration.
            configValues = Collections.unmodifiableMap(loadedValues);
            localizationManager.init(getString("language", "en"));
        } catch (Exception e) {
            logger.severe("Failed to load config.yml; keeping the last valid configuration. "
                    + "File contents and exception details are hidden.");
        }
    }

    private void saveDefaultIfNotExists(File targetFile, String resourceName) {
        if (!targetFile.exists()) {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourceName)) {
                if (in != null) {
                    Files.copy(in, targetFile.toPath());
                } else {
                    targetFile.createNewFile();
                }
            } catch (Exception e) {
                logger.warning("Could not create default file: " + resourceName);
            }
        }
    }

    public String getString(String path, String defaultValue) {
        Object val = getValue(path);
        if (val instanceof String str) {
            return resolveEnvVars(str);
        }
        return defaultValue;
    }

    public int getInt(String path, int defaultValue) {
        Object val = getValue(path);
        if (val instanceof Number num) {
            return num.intValue();
        }
        return defaultValue;
    }

    public int getInt(String path, int defaultValue, int minimum, int maximum) {
        if (minimum > maximum || defaultValue < minimum || defaultValue > maximum) {
            throw new IllegalArgumentException("Invalid numeric bounds for " + path);
        }
        int value = getInt(path, defaultValue);
        if (value < minimum || value > maximum) {
            logger.warning("Configuration value '" + path + "' must be between " + minimum
                    + " and " + maximum + "; using " + defaultValue + ".");
            return defaultValue;
        }
        return value;
    }

    public boolean getBoolean(String path, boolean defaultValue) {
        Object val = getValue(path);
        if (val instanceof Boolean b) {
            return b;
        }
        return defaultValue;
    }

    public List<String> getStringList(String path, List<String> defaultValue) {
        Object val = getValue(path);
        if (val instanceof List<?> list && list.stream().allMatch(String.class::isInstance)) {
            return list.stream().map(String.class::cast).toList();
        }
        return defaultValue;
    }

    public String getMessage(String path, String defaultValue) {
        return localizationManager.getMessage(path, defaultValue);
    }

    public String getMessage(String path, String defaultValue, Map<String, String> placeholders) {
        return localizationManager.getMessage(path, defaultValue, placeholders);
    }

    public LocalizationManager getLocalizationManager() {
        return localizationManager;
    }

    private Object getValue(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        return configValues.get(path);
    }

    private static Map<String, Object> flatten(Map<?, ?> root) {
        Map<String, Object> flattened = new LinkedHashMap<>();
        flattenInto(root, "", flattened);
        return flattened;
    }

    private static void flattenInto(Map<?, ?> source, String prefix, Map<String, Object> target) {
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (!(entry.getKey() instanceof String key) || key.isBlank()) {
                throw new IllegalArgumentException("all YAML keys must be non-empty strings");
            }
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> nested) {
                flattenInto(nested, path, target);
            } else {
                target.put(path, value);
            }
        }
    }

    private static String resolveEnvVars(String value) {
        if (value.startsWith("${ENV:") && value.endsWith("}")) {
            String envVarName = value.substring(6, value.length() - 1);
            String envValue = System.getenv(envVarName);
            return envValue != null ? envValue : "";
        }
        return value;
    }
}
