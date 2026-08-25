package com.xigua.geyserupdate.common;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GeyserDownloadClient {
    private static final String API_BASE = "https://download.geysermc.org/v2/projects";

    private final HttpClient httpClient;
    private final UpdateConfig config;

    public GeyserDownloadClient(UpdateConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(config.connectTimeoutMillis()))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public RemoteBuild fetchLatestBuild(PluginProject project, ServerPlatform platform) throws UpdateException {
        if (project.source() == PluginProject.Source.HANGAR_PAPER_SNAPSHOT) {
            return fetchLatestHangarPaperSnapshot(project, platform);
        }
        if (project.source() == PluginProject.Source.HANGAR_WATERFALL_RELEASE) {
            return fetchLatestHangarWaterfallRelease(project, platform);
        }
        return fetchLatestGeyserBuild(project, platform);
    }

    private RemoteBuild fetchLatestGeyserBuild(PluginProject project, ServerPlatform platform) throws UpdateException {
        String metadataUrl = API_BASE + "/" + project.apiId() + "/versions/latest/builds/latest";
        String downloadKey = project.downloadKey(platform);
        String json = sendString(metadataUrl);

        String version = requireString(json, "version", project.displayName());
        int build = requireInt(json, "build", project.displayName());
        String time = requireString(json, "time", project.displayName());
        String downloadObject = requireDownloadObject(json, downloadKey, project.displayName());
        String fileName = requireString(downloadObject, "name", project.displayName());
        String sha256 = requireString(downloadObject, "sha256", project.displayName()).toLowerCase();
        String downloadUrl = metadataUrl + "/downloads/" + downloadKey;
        return new RemoteBuild(project, version, build, time, downloadKey, fileName, sha256, downloadUrl);
    }

    private RemoteBuild fetchLatestHangarPaperSnapshot(PluginProject project, ServerPlatform platform) throws UpdateException {
        if (platform != ServerPlatform.PAPER) {
            throw new UpdateException(project.displayName() + " 测试版只支持 Paper 端自动更新。");
        }
        String metadataUrl = "https://hangar.papermc.io/api/v1/projects/ViaVersion/"
                + project.hangarProjectSlug()
                + "/versions?limit=1&offset=0&channel=Snapshot&platform=Paper";
        String json = sendString(metadataUrl);
        String versionObject = requireObject(json, "result", project.displayName());
        String versionName = requireString(versionObject, "name", project.displayName());
        int versionId = requireInt(versionObject, "id", project.displayName());
        String time = requireString(versionObject, "createdAt", project.displayName());
        String downloads = requireObject(versionObject, "downloads", project.displayName());
        String paperDownload = requireObject(downloads, "PAPER", project.displayName());
        String fileInfo = requireObject(paperDownload, "fileInfo", project.displayName());
        String fileName = requireString(fileInfo, "name", project.displayName());
        String sha256 = requireString(fileInfo, "sha256Hash", project.displayName()).toLowerCase();
        String downloadUrl = requireString(paperDownload, "downloadUrl", project.displayName());
        return new RemoteBuild(project, versionWithoutBuild(versionName), buildFromVersionName(versionName, versionId), time, "paper-snapshot", fileName, sha256, downloadUrl);
    }

    private RemoteBuild fetchLatestHangarWaterfallRelease(PluginProject project, ServerPlatform platform) throws UpdateException {
        if (platform != ServerPlatform.BUNGEE) {
            throw new UpdateException(project.displayName() + " 自动更新只支持 BC/Waterfall 端。");
        }
        String metadataUrl = "https://hangar.papermc.io/api/v1/projects/SRTeam/"
                + project.hangarProjectSlug()
                + "/versions?limit=1&offset=0&channel=Release&platform=WATERFALL";
        String json = sendString(metadataUrl);
        String versionObject = requireObject(json, "result", project.displayName());
        String versionName = requireString(versionObject, "name", project.displayName());
        int versionId = requireInt(versionObject, "id", project.displayName());
        String time = requireString(versionObject, "createdAt", project.displayName());
        String downloads = requireObject(versionObject, "downloads", project.displayName());
        String waterfallDownload = requireObject(downloads, "WATERFALL", project.displayName());
        String fileInfo = requireObject(waterfallDownload, "fileInfo", project.displayName());
        String fileName = requireString(fileInfo, "name", project.displayName());
        String sha256 = requireString(fileInfo, "sha256Hash", project.displayName()).toLowerCase();
        String downloadUrl = requireString(waterfallDownload, "downloadUrl", project.displayName());
        return new RemoteBuild(project, versionName, versionId, time, "waterfall-release", fileName, sha256, downloadUrl);
    }

    public DownloadStream openDownload(RemoteBuild build) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(build.downloadUrl()))
                .timeout(Duration.ofMillis(config.readTimeoutMillis()))
                .header("User-Agent", "GeyserUpdate/1.0")
                .GET()
                .build();
        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("下载请求失败，HTTP 状态码：" + response.statusCode());
        }
        long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
        return new DownloadStream(response.body(), contentLength);
    }

    private String sendString(String url) throws UpdateException {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(config.readTimeoutMillis()))
                    .header("User-Agent", "GeyserUpdate/1.0")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new UpdateException("读取官方更新信息失败，HTTP 状态码：" + response.statusCode());
            }
            return response.body();
        } catch (IOException e) {
            throw new UpdateException("读取官方更新信息时发生网络错误。", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UpdateException("读取官方更新信息被中断。", e);
        } catch (IllegalArgumentException e) {
            throw new UpdateException("官方更新地址格式无效。", e);
        }
    }

    private static String requireDownloadObject(String json, String downloadKey, String projectName) throws UpdateException {
        String downloads = requireObject(json, "downloads", projectName);
        return requireObject(downloads, downloadKey, projectName);
    }

    private static String requireObject(String json, String key, String projectName) throws UpdateException {
        Pattern keyPattern = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:");
        Matcher matcher = keyPattern.matcher(json);
        if (!matcher.find()) {
            throw new UpdateException("官方更新信息中缺少 " + projectName + " 的字段：" + key);
        }
        int braceStart = json.indexOf('{', matcher.end());
        if (braceStart < 0) {
            throw new UpdateException("官方更新信息中字段格式异常：" + key);
        }
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = braceStart; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escape) {
                escape = false;
                continue;
            }
            if (c == '\\' && inString) {
                escape = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return json.substring(braceStart, i + 1);
                }
            }
        }
        throw new UpdateException("官方更新信息中字段对象未正确结束：" + key);
    }

    private static String requireString(String json, String key, String projectName) throws UpdateException {
        Pattern pattern = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"");
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            throw new UpdateException("官方更新信息中缺少 " + projectName + " 的字段：" + key);
        }
        return unescapeJsonString(matcher.group(1));
    }

    private static int requireInt(String json, String key, String projectName) throws UpdateException {
        Pattern pattern = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*(\\d+)");
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            throw new UpdateException("官方更新信息中缺少 " + projectName + " 的数字字段：" + key);
        }
        return Integer.parseInt(matcher.group(1));
    }

    private static String versionWithoutBuild(String versionName) {
        int plus = versionName.lastIndexOf('+');
        if (plus <= 0) {
            return versionName;
        }
        return versionName.substring(0, plus);
    }

    private static int buildFromVersionName(String versionName, int fallback) {
        int plus = versionName.lastIndexOf('+');
        if (plus < 0 || plus + 1 >= versionName.length()) {
            return fallback;
        }
        try {
            return Integer.parseInt(versionName.substring(plus + 1));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String unescapeJsonString(String value) {
        StringBuilder out = new StringBuilder(value.length());
        boolean escape = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!escape) {
                if (c == '\\') {
                    escape = true;
                } else {
                    out.append(c);
                }
                continue;
            }

            switch (c) {
                case '"' -> out.append('"');
                case '\\' -> out.append('\\');
                case '/' -> out.append('/');
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'u' -> {
                    if (i + 4 < value.length()) {
                        String hex = value.substring(i + 1, i + 5);
                        try {
                            out.append((char) Integer.parseInt(hex, 16));
                            i += 4;
                        } catch (NumberFormatException ignored) {
                            out.append("\\u").append(hex);
                            i += 4;
                        }
                    } else {
                        out.append("\\u");
                    }
                }
                default -> out.append(c);
            }
            escape = false;
        }
        if (escape) {
            out.append('\\');
        }
        return out.toString();
    }
}


