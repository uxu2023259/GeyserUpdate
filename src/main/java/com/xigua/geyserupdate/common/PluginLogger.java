package com.xigua.geyserupdate.common;

public interface PluginLogger {
    void info(String message);

    void warn(String message);

    void error(String message);

    void error(String message, Throwable throwable);
}
