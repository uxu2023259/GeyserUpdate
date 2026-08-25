package com.xigua.geyserupdate.common;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

public final class ExternalUpdateApplier {
    private ExternalUpdateApplier() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("参数不足，无法执行离线替换。 ");
            System.exit(2);
            return;
        }

        long parentPid = Long.parseLong(args[0]);
        Path manifest = Path.of(args[1]).toAbsolutePath();
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(manifest)) {
            properties.load(in);
        }

        Path logFile = Path.of(properties.getProperty("logFile")).toAbsolutePath();
        log(logFile, "离线更新器已启动，等待服务器进程退出，PID：" + parentPid);
        waitForExit(parentPid, logFile);

        int count = Integer.parseInt(properties.getProperty("count", "0"));
        List<String> applied = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String prefix = "item." + i + ".";
            Path source = Path.of(properties.getProperty(prefix + "source")).toAbsolutePath();
            Path target = Path.of(properties.getProperty(prefix + "target")).toAbsolutePath();
            String project = properties.getProperty(prefix + "project", "插件");

            Files.createDirectories(target.getParent());
            deleteOfficialOldJars(target.getParent(), target.getFileName().toString(), project, logFile);
            try {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailure) {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
            applied.add(target.getFileName().toString());
            log(logFile, project + " 已在服务器关闭后替换完成：" + target);
        }

        properties.setProperty("applied", String.join(",", applied));
        properties.setProperty("appliedAt", Instant.now().toString());
        try (OutputStream out = Files.newOutputStream(manifest)) {
            properties.store(out, "GeyserUpdate 离线替换结果");
        }
        log(logFile, "离线更新器执行完成，本次替换文件数量：" + count);
    }

    private static void waitForExit(long pid, Path logFile) throws InterruptedException, IOException {
        if (pid <= 0) {
            Thread.sleep(5000L);
            return;
        }
        long start = System.currentTimeMillis();
        while (ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)) {
            Thread.sleep(1000L);
            if (System.currentTimeMillis() - start > 180_000L) {
                log(logFile, "等待服务器进程退出已超过 180 秒，继续等待。 ");
                start = System.currentTimeMillis();
            }
        }
        Thread.sleep(1500L);
    }

    private static void deleteOfficialOldJars(Path pluginsFolder, String keepFileName, String project, Path logFile) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(pluginsFolder, "*.jar")) {
            for (Path jar : stream) {
                String name = jar.getFileName().toString();
                if (!name.equals(keepFileName) && isOfficialPluginJar(project, name)) {
                    Files.deleteIfExists(jar);
                    log(logFile, "已清理旧的 " + project + " 文件：" + name);
                }
            }
        }
    }

    private static boolean isOfficialPluginJar(String project, String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if ("Geyser".equalsIgnoreCase(project)) {
            return lower.equals("geyser-spigot.jar")
                    || lower.equals("geyser-bungeecord.jar")
                    || lower.startsWith("geyser-spigot-")
                    || lower.startsWith("geyser-bungeecord-");
        }
        if ("Floodgate".equalsIgnoreCase(project)) {
            return lower.equals("floodgate-spigot.jar")
                    || lower.equals("floodgate-bungee.jar")
                    || lower.startsWith("floodgate-spigot-")
                    || lower.startsWith("floodgate-bungee-");
        }
        if ("ViaVersion".equalsIgnoreCase(project)) {
            return lower.equals("viaversion.jar") || isVersionedJar(lower, "viaversion");
        }
        if ("ViaBackwards".equalsIgnoreCase(project)) {
            return lower.equals("viabackwards.jar") || isVersionedJar(lower, "viabackwards");
        }
        if ("ViaRewind".equalsIgnoreCase(project)) {
            return lower.equals("viarewind.jar") || isVersionedJar(lower, "viarewind");
        }
        if ("ViaRewind-Legacy-Support".equalsIgnoreCase(project)) {
            return lower.equals("viarewind-legacy-support.jar")
                    || lower.equals("viarewindlegacysupport.jar")
                    || isVersionedJar(lower, "viarewind-legacy-support")
                    || isVersionedJar(lower, "viarewindlegacysupport");
        }
        if ("SkinsRestorer".equalsIgnoreCase(project)) {
            return lower.equals("skinsrestorer.jar") || isVersionedJar(lower, "skinsrestorer");
        }
        return false;
    }

    private static boolean isVersionedJar(String lowerFileName, String lowerPrefix) {
        int versionStart = lowerPrefix.length() + 1;
        return lowerFileName.startsWith(lowerPrefix + "-")
                && lowerFileName.endsWith(".jar")
                && lowerFileName.length() > versionStart
                && Character.isDigit(lowerFileName.charAt(versionStart));
    }

    private static void log(Path logFile, String message) throws IOException {
        Files.createDirectories(logFile.getParent());
        String line = "[" + Instant.now() + "] " + message + System.lineSeparator();
        Files.writeString(logFile, line, StandardCharsets.UTF_8, Files.exists(logFile)
                ? java.nio.file.StandardOpenOption.APPEND
                : java.nio.file.StandardOpenOption.CREATE);
    }
}
