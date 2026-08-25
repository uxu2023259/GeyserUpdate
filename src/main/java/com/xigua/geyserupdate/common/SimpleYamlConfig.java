package com.xigua.geyserupdate.common;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SimpleYamlConfig {
    private final Path file;
    private final Map<String, String> values = new LinkedHashMap<>();

    private SimpleYamlConfig(Path file) {
        this.file = file;
    }

    public static SimpleYamlConfig load(Path dataFolder, InputStream defaultConfig) throws IOException {
        Files.createDirectories(dataFolder);
        Path file = dataFolder.resolve("config.yml");
        if (Files.notExists(file)) {
            try (InputStream in = defaultConfig; OutputStream out = Files.newOutputStream(file)) {
                if (in == null) {
                    throw new IOException("未找到默认配置文件 config.yml");
                }
                in.transferTo(out);
            }
        } else if (defaultConfig != null) {
            defaultConfig.close();
        }

        SimpleYamlConfig config = new SimpleYamlConfig(file);
        config.reload();
        return config;
    }

    public void reload() throws IOException {
        values.clear();
        for (String rawLine : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int split = line.indexOf(':');
            if (split <= 0) {
                continue;
            }
            String key = line.substring(0, split).trim();
            String value = line.substring(split + 1).trim();
            int comment = findCommentStart(value);
            if (comment >= 0) {
                value = value.substring(0, comment).trim();
            }
            values.put(key, stripQuotes(value));
        }
    }

    public boolean getBoolean(String key, boolean fallback) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return Boolean.parseBoolean(value.trim());
    }

    public int getInt(String key, int fallback) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public long getLong(String key, long fallback) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public String getString(String key, String fallback) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    public Path file() {
        return file;
    }

    private static int findCommentStart(String value) {
        boolean singleQuote = false;
        boolean doubleQuote = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\'' && !doubleQuote) {
                singleQuote = !singleQuote;
            } else if (c == '"' && !singleQuote) {
                doubleQuote = !doubleQuote;
            } else if (c == '#' && !singleQuote && !doubleQuote) {
                return i;
            }
        }
        return -1;
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }
}
