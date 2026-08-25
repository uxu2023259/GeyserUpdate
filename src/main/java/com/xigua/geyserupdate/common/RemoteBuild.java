package com.xigua.geyserupdate.common;

public record RemoteBuild(
        PluginProject project,
        String version,
        int build,
        String time,
        String downloadKey,
        String fileName,
        String sha256,
        String downloadUrl
) {
    public String displayVersion() {
        return version + " 构建 " + build;
    }
}
