package com.xigua.geyserupdate.common;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public enum PluginProject {
    GEYSER(Source.GEYSER_DOWNLOAD, "geyser", null, "Geyser", "Geyser", "Geyser-Spigot", "Geyser-BungeeCord"),
    FLOODGATE(Source.GEYSER_DOWNLOAD, "floodgate", null, "Floodgate", "floodgate"),
    VIAVERSION(Source.HANGAR_PAPER_SNAPSHOT, "viaversion", "ViaVersion", "ViaVersion", "ViaVersion"),
    VIABACKWARDS(Source.HANGAR_PAPER_SNAPSHOT, "viabackwards", "ViaBackwards", "ViaBackwards", "ViaBackwards"),
    VIAREWIND(Source.HANGAR_PAPER_SNAPSHOT, "viarewind", "ViaRewind", "ViaRewind", "ViaRewind"),
    VIAREWIND_LEGACY_SUPPORT(Source.HANGAR_PAPER_SNAPSHOT, "viarewindlegacysupport", "ViaRewindLegacySupport", "ViaRewind-Legacy-Support", "ViaRewind-Legacy-Support", "ViaRewindLegacySupport"),
    SKINS_RESTORER(Source.HANGAR_WATERFALL_RELEASE, "skinsrestorer", "SkinsRestorer", "SkinsRestorer", "SkinsRestorer");

    private final Source source;
    private final String apiId;
    private final String hangarProjectSlug;
    private final String displayName;
    private final Set<String> pluginNames;

    PluginProject(Source source, String apiId, String hangarProjectSlug, String displayName, String... pluginNames) {
        this.source = source;
        this.apiId = apiId;
        this.hangarProjectSlug = hangarProjectSlug;
        this.displayName = displayName;
        this.pluginNames = normalizePluginNames(pluginNames);
    }

    public Source source() {
        return source;
    }

    public String apiId() {
        return apiId;
    }

    public String hangarProjectSlug() {
        return hangarProjectSlug;
    }

    public String displayName() {
        return displayName;
    }

    public boolean supportsPlatform(ServerPlatform platform) {
        return source == Source.GEYSER_DOWNLOAD
                || (source == Source.HANGAR_PAPER_SNAPSHOT && platform == ServerPlatform.PAPER)
                || (source == Source.HANGAR_WATERFALL_RELEASE && platform == ServerPlatform.BUNGEE);
    }

    public boolean shouldUpdateWhenNotLoaded(ServerPlatform platform) {
        return (source == Source.HANGAR_PAPER_SNAPSHOT && platform == ServerPlatform.PAPER)
                || (this == SKINS_RESTORER && platform == ServerPlatform.BUNGEE);
    }

    public boolean matchesPluginName(String pluginName) {
        return pluginName != null && pluginNames.contains(pluginName.trim().toLowerCase(Locale.ROOT));
    }

    public String downloadKey(ServerPlatform platform) {
        if (this == GEYSER) {
            return platform.geyserDownloadKey();
        }
        return platform.floodgateDownloadKey();
    }

    private static Set<String> normalizePluginNames(String[] pluginNames) {
        Set<String> normalized = new LinkedHashSet<>();
        for (String pluginName : pluginNames) {
            if (pluginName != null && !pluginName.isBlank()) {
                normalized.add(pluginName.trim().toLowerCase(Locale.ROOT));
            }
        }
        return Set.copyOf(normalized);
    }

    public enum Source {
        GEYSER_DOWNLOAD,
        HANGAR_PAPER_SNAPSHOT,
        HANGAR_WATERFALL_RELEASE
    }
}
