package com.xigua.geyserupdate.paper;

import com.xigua.geyserupdate.common.PluginLogger;

import java.util.logging.Level;
import java.util.logging.Logger;

public final class PaperPluginLogger implements PluginLogger {
    private final Logger logger;

    public PaperPluginLogger(Logger logger) {
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
