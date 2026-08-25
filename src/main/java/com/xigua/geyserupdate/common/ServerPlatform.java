package com.xigua.geyserupdate.common;

public enum ServerPlatform {
    PAPER("Paper", "spigot", "spigot", "paper-shutdown-command"),
    BUNGEE("BC/Waterfall", "bungeecord", "bungee", "bungee-shutdown-command");

    private final String displayName;
    private final String geyserDownloadKey;
    private final String floodgateDownloadKey;
    private final String shutdownCommandKey;

    ServerPlatform(String displayName, String geyserDownloadKey, String floodgateDownloadKey, String shutdownCommandKey) {
        this.displayName = displayName;
        this.geyserDownloadKey = geyserDownloadKey;
        this.floodgateDownloadKey = floodgateDownloadKey;
        this.shutdownCommandKey = shutdownCommandKey;
    }

    public String displayName() {
        return displayName;
    }

    public String geyserDownloadKey() {
        return geyserDownloadKey;
    }

    public String floodgateDownloadKey() {
        return floodgateDownloadKey;
    }

    public String shutdownCommandKey() {
        return shutdownCommandKey;
    }
}
