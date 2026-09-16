package io.github.touchfalls.authsecured.fabric.adapter;

import io.github.touchfalls.authsecured.ports.PlatformLogger;
import org.slf4j.Logger;

public final class FabricLogger implements PlatformLogger {

    private final Logger logger;

    public FabricLogger(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void info(String message) {
        logger.info(message);
    }

    @Override
    public void warning(String message) {
        logger.warn(message);
    }

    @Override
    public void severe(String message) {
        logger.error(message);
    }

    @Override
    public void severe(String message, Throwable throwable) {
        logger.error(message, throwable);
    }
}
