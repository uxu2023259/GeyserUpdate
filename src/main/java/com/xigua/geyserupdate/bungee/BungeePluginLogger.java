package com.xigua.geyserupdate.bungee;

import com.xigua.geyserupdate.common.PluginLogger;

import java.util.logging.Level;
import java.util.logging.Logger;

public final class BungeePluginLogger implements PluginLogger {
    private final Logger logger;

    public BungeePluginLogger(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void info(String message) {
        logger.info(message);
    }

    @Override
    public void warn(String message) {
        logger.warning(message);
    }

    @Override
    public void error(String message) {
        logger.severe(message);
    }

    @Override
    public void error(String message, Throwable throwable) {
        logger.log(Level.SEVERE, message, throwable);
    }
}
