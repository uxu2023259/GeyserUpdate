package com.xigua.geyserupdate.common;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.function.Supplier;

public final class UpdateService {
    private final ServerPlatform platform;
    private final Path pluginsFolder;
    private final Path dataFolder;
    private final PluginLogger logger;
    private final UpdateConfig config;
    private final Supplier<Set<String>> loadedPluginNamesSupplier;
    private final GeyserDownloadClient client;
    private final Runnable shutdownAction;
    private final Object lock = new Object();
    private volatile boolean running;

    public UpdateService(
            ServerPlatform platform,
            Path pluginsFolder,
            Path dataFolder,
            PluginLogger logger,
            UpdateConfig config,
            Supplier<Set<String>> loadedPluginNamesSupplier,
            Runnable shutdownAction
    ) {
        this.platform = platform;
        this.pluginsFolder = pluginsFolder;
        this.dataFolder = dataFolder;
        this.logger = logger;
        this.config = config;
        this.loadedPluginNamesSupplier = loadedPluginNamesSupplier;
        this.client = new GeyserDownloadClient(config);
        this.shutdownAction = shutdownAction;
    }

    public boolean checkAndUpdate(String reason) {
        return checkAndUpdate(reason, false);
    }

    public boolean checkAndUpdate(String reason, boolean allowAutoShutdown) {
        synchronized (lock) {
            if (running) {
                logger.warn("已有更新检测任务正在运行，本次请求已跳过。原因：" + reason);
                return false;
            }
            running = true;
        }

        List<PendingUpdate> pendingUpdates = new ArrayList<>();
        try {
            Files.createDirectories(dataFolder);
            Files.createDirectories(pluginsFolder);
            List<PluginProject> targetProjects = loadedProjects();
            if (targetProjects.isEmpty()) {
                logger.warn("未检测到需要更新的插件项目，本次不会更新任何文件。原因：" + reason);
                return false;
            }

            logger.info("开始检测已加载插件更新：" + formatProjectNames(targetProjects) + "，当前端类型：" + platform.displayName() + "，原因：" + reason);

            for (PluginProject project : targetProjects) {
                PendingUpdate pending = checkProject(project);
                if (pending != null) {
                    pendingUpdates.add(pending);
                }
            }

            if (!pendingUpdates.isEmpty()) {
                Path manifest = writePendingManifest(pendingUpdates);
                startExternalApplier(manifest);
                logger.info("本次更新文件已下载并校验完成，已交给离线更新器。服务器关闭后会自动替换为新版本。 ");
                if (allowAutoShutdown && config.autoShutdownAfterUpdate()) {
                    logger.info("本次为每日定时检测，允许自动关闭服务器。即将执行关闭命令：" + config.shutdownCommand());
                    shutdownAction.run();
                } else if (!allowAutoShutdown) {
                    logger.warn("本次不是每日定时检测，不会自动关闭服务器。请管理员在方便时手动重启，重启后会自动换成新版本。 ");
                } else {
                    logger.warn("配置已关闭自动执行关闭命令，请管理员手动重启；重启后会自动换成新版本。 ");
                }
                return true;
            }

            logger.info("检测完成：当前已加载的 " + formatProjectNames(targetProjects) + " 均无需更新。 ");
            return false;
        } catch (Exception e) {
            logger.error("自动更新任务失败，已保留原有插件文件，稍后会在下次检测时重试。", e);
            return false;
        } finally {
            running = false;
        }
    }

    public boolean isRunning() {
        return running;
    }

    private List<PluginProject> loadedProjects() {
        Set<String> loadedPluginNames = loadedPluginNames();
        List<PluginProject> projects = new ArrayList<>();
        for (PluginProject project : PluginProject.values()) {
            if (!project.supportsPlatform(platform)) {
                continue;
            }
            if (project.shouldUpdateWhenNotLoaded(platform)) {
                projects.add(project);
            } else if (matchesLoadedPlugin(project, loadedPluginNames)) {
                projects.add(project);
            } else {
                logger.info("未检测到已加载的 " + project.displayName() + " 插件，本次跳过该项目。 ");
            }
        }
        return projects;
    }

    private Set<String> loadedPluginNames() {
        Set<String> loadedPluginNames = loadedPluginNamesSupplier.get();
        if (loadedPluginNames == null || loadedPluginNames.isEmpty()) {
            return Set.of();
        }
        return loadedPluginNames;
    }

    private boolean matchesLoadedPlugin(PluginProject project, Set<String> loadedPluginNames) {
        for (String pluginName : loadedPluginNames) {
            if (project.matchesPluginName(pluginName)) {
                return true;
            }
        }
        return false;
    }

    private String formatProjectNames(List<PluginProject> projects) {
        StringBuilder builder = new StringBuilder();
        for (PluginProject project : projects) {
            if (!builder.isEmpty()) {
                builder.append("、");
            }
            builder.append(project.displayName());
        }
        return builder.toString();
    }

    private PendingUpdate checkProject(PluginProject project) throws UpdateException, IOException {
        RemoteBuild remote = client.fetchLatestBuild(project, platform);
        logger.info(project.displayName() + " 官方最新版本：" + remote.displayVersion() + "，文件：" + remote.fileName());

        Path target = pluginsFolder.resolve(remote.fileName());
        if (!needsUpdate(remote, target)) {
            logger.info(project.displayName() + " 已是最新版本，无需更新。 ");
            saveInstalledRecord(remote, target, "已是最新版本");
            return null;
        }

        logger.warn(project.displayName() + " 需要更新，准备下载：" + remote.fileName());
        Path downloaded = download(remote);
        String actualSha = sha256(downloaded);
        if (!actualSha.equalsIgnoreCase(remote.sha256())) {
            Files.deleteIfExists(downloaded);
            throw new UpdateException(project.displayName() + " 下载文件校验失败，期望 SHA-256：" + remote.sha256() + "，实际：" + actualSha);
        }
        logger.info(project.displayName() + " 下载校验成功，SHA-256：" + actualSha);

        Path pendingFile = moveToPending(remote, downloaded);
        saveInstalledRecord(remote, target, "等待重启后替换");
        logger.info(project.displayName() + " 已准备好重启后更新：" + pendingFile.toAbsolutePath());
        return new PendingUpdate(remote, pendingFile, target);
    }

    private boolean needsUpdate(RemoteBuild remote, Path target) throws IOException {
        if (Files.exists(target)) {
            String localSha = sha256(target);
            if (localSha.equalsIgnoreCase(remote.sha256())) {
                return false;
            }
            logger.warn(remote.project().displayName() + " 本地文件存在但校验值不同，将进行更新。当前文件：" + target.getFileName());
            return true;
        }
        return true;
    }

    private Path download(RemoteBuild remote) throws UpdateException {
        Path tempFile = dataFolder.resolve(remote.project().apiId() + "-" + remote.downloadKey() + ".download");
        try {
            Files.deleteIfExists(tempFile);
            long lastLog = 0L;
            long total = 0L;
            long start = System.nanoTime();
            try (DownloadStream download = client.openDownload(remote); InputStream in = download.inputStream(); OutputStream out = Files.newOutputStream(tempFile)) {
                long contentLength = download.contentLength();
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    out.write(buffer, 0, read);
                    total += read;
                    long now = System.currentTimeMillis();
                    if (now - lastLog >= config.progressLogIntervalMillis()) {
                        logger.info(remote.project().displayName() + " 下载进度：" + formatPercent(total, contentLength) + "，已下载 " + formatBytes(total) + formatTotalBytes(contentLength) + "，速度 " + formatSpeed(total, start));
                        lastLog = now;
                    }
                }
            }
            logger.info(remote.project().displayName() + " 下载完成：" + formatPercent(total, total) + "，总大小 " + formatBytes(total) + "，平均速度 " + formatSpeed(total, start));
            return tempFile;
        } catch (IOException e) {
            throw new UpdateException(remote.project().displayName() + " 下载失败。", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UpdateException(remote.project().displayName() + " 下载被中断。", e);
        }
    }

    private Path moveToPending(RemoteBuild remote, Path downloaded) throws IOException {
        Path pendingFolder = dataFolder.resolve("pending");
        Files.createDirectories(pendingFolder);
        Path pendingFile = pendingFolder.resolve(remote.fileName());
        Files.move(downloaded, pendingFile, StandardCopyOption.REPLACE_EXISTING);
        return pendingFile;
    }

    private Path writePendingManifest(List<PendingUpdate> updates) throws IOException {
        Path pendingFolder = dataFolder.resolve("pending");
        Files.createDirectories(pendingFolder);
        Properties properties = new Properties();
        properties.setProperty("createdAt", Instant.now().toString());
        properties.setProperty("platform", platform.name());
        properties.setProperty("pid", String.valueOf(ProcessHandle.current().pid()));
        properties.setProperty("count", String.valueOf(updates.size()));
        properties.setProperty("logFile", dataFolder.resolve("offline-update.log").toAbsolutePath().toString());
        for (int i = 0; i < updates.size(); i++) {
            PendingUpdate update = updates.get(i);
            String prefix = "item." + i + ".";
            properties.setProperty(prefix + "project", update.remote().project().displayName());
            properties.setProperty(prefix + "version", update.remote().version());
            properties.setProperty(prefix + "build", String.valueOf(update.remote().build()));
            properties.setProperty(prefix + "sha256", update.remote().sha256());
            properties.setProperty(prefix + "source", update.pendingFile().toAbsolutePath().toString());
            properties.setProperty(prefix + "target", update.target().toAbsolutePath().toString());
        }
        Path manifest = pendingFolder.resolve("pending-update.properties");
        try (OutputStream out = Files.newOutputStream(manifest)) {
            properties.store(out, "GeyserUpdate 待重启替换任务");
        }
        return manifest;
    }

    private void startExternalApplier(Path manifest) throws IOException, URISyntaxException {
        Path currentJar = Path.of(UpdateService.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath();
        if (!Files.isRegularFile(currentJar)) {
            throw new IOException("无法定位当前插件 JAR，不能启动离线更新器：" + currentJar);
        }
        String javaExecutable = Path.of(System.getProperty("java.home"), "bin", isWindows() ? "javaw.exe" : "java").toString();
        if (!Files.exists(Path.of(javaExecutable))) {
            javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        }
        new ProcessBuilder(
                javaExecutable,
                "-cp",
                currentJar.toString(),
                ExternalUpdateApplier.class.getName(),
                String.valueOf(ProcessHandle.current().pid()),
                manifest.toAbsolutePath().toString()
        ).directory(pluginsFolder.toFile()).start();
        logger.info("离线更新器已启动，将在当前服务器进程退出后替换已锁定的插件文件。任务文件：" + manifest.toAbsolutePath());
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private Properties loadRecords() throws IOException {
        Properties properties = new Properties();
        Path file = recordsFile();
        if (Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                properties.load(in);
            }
        }
        return properties;
    }

    private void saveInstalledRecord(RemoteBuild remote, Path target, String status) throws IOException {
        Properties properties = loadRecords();
        String prefix = recordPrefix(remote.project());
        properties.setProperty(prefix + "version", remote.version());
        properties.setProperty(prefix + "build", String.valueOf(remote.build()));
        properties.setProperty(prefix + "time", remote.time());
        properties.setProperty(prefix + "downloadKey", remote.downloadKey());
        properties.setProperty(prefix + "file", remote.fileName());
        properties.setProperty(prefix + "sha256", remote.sha256());
        properties.setProperty(prefix + "path", target.toAbsolutePath().toString());
        properties.setProperty(prefix + "status", status);
        properties.setProperty(prefix + "checkedAt", Instant.now().toString());
        Path file = recordsFile();
        try (OutputStream out = Files.newOutputStream(file)) {
            properties.store(out, "GeyserUpdate 自动更新记录");
        }
    }

    private Path recordsFile() {
        return dataFolder.resolve("installed.properties");
    }

    private String recordPrefix(PluginProject project) {
        return platform.name().toLowerCase(Locale.ROOT) + "." + project.apiId() + ".";
    }

    private static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            byte[] hash = digest.digest();
            StringBuilder out = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                out.append(String.format("%02x", b));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("当前 Java 环境不支持 SHA-256 校验。", e);
        }
    }

    private static String formatPercent(long downloaded, long total) {
        if (total <= 0) {
            return "百分比未知";
        }
        double percent = Math.min(100.0, downloaded * 100.0 / total);
        return String.format(Locale.ROOT, "%.2f%%", percent);
    }

    private static String formatTotalBytes(long total) {
        if (total <= 0) {
            return "";
        }
        return "/" + formatBytes(total);
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kib = bytes / 1024.0;
        if (kib < 1024) {
            return String.format(Locale.ROOT, "%.2f KiB", kib);
        }
        double mib = kib / 1024.0;
        return String.format(Locale.ROOT, "%.2f MiB", mib);
    }

    private static String formatSpeed(long bytes, long startNanos) {
        double seconds = Math.max(0.001, (System.nanoTime() - startNanos) / 1_000_000_000.0);
        return formatBytes((long) (bytes / seconds)) + "/秒";
    }

    private record PendingUpdate(RemoteBuild remote, Path pendingFile, Path target) {
    }
}

