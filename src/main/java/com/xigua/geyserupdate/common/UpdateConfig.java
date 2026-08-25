package com.xigua.geyserupdate.common;

public final class UpdateConfig {
    private final boolean checkOnStart;
    private final String dailyCheckTime;
    private final String shutdownCommand;
    private final boolean autoShutdownAfterUpdate;
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;
    private final long progressLogIntervalMillis;

    private UpdateConfig(
            boolean checkOnStart,
            String dailyCheckTime,
            String shutdownCommand,
            boolean autoShutdownAfterUpdate,
            int connectTimeoutMillis,
            int readTimeoutMillis,
            long progressLogIntervalMillis
    ) {
        this.checkOnStart = checkOnStart;
        this.dailyCheckTime = dailyCheckTime;
        this.shutdownCommand = shutdownCommand;
        this.autoShutdownAfterUpdate = autoShutdownAfterUpdate;
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
        this.progressLogIntervalMillis = progressLogIntervalMillis;
    }

    public static UpdateConfig from(SimpleYamlConfig config, ServerPlatform platform) {
        String defaultShutdownCommand = platform == ServerPlatform.BUNGEE ? "end" : "stop";
        return new UpdateConfig(
                config.getBoolean("check-on-start", true),
                config.getString("daily-check-time", "04:00"),
                config.getString(platform.shutdownCommandKey(), defaultShutdownCommand),
                config.getBoolean("auto-shutdown-after-update", true),
                config.getInt("connect-timeout-millis", 15000),
                config.getInt("read-timeout-millis", 30000),
                config.getLong("progress-log-interval-millis", 1000L)
        );
    }

    public boolean checkOnStart() {
        return checkOnStart;
    }

    public String dailyCheckTime() {
        return dailyCheckTime;
    }

    public String shutdownCommand() {
        return shutdownCommand;
    }

    public boolean autoShutdownAfterUpdate() {
        return autoShutdownAfterUpdate;
    }

    public int connectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    public int readTimeoutMillis() {
        return readTimeoutMillis;
    }

    public long progressLogIntervalMillis() {
        return progressLogIntervalMillis;
    }
}
