# Errors

Command failures and integration errors.

---
## [ERR-20260627-001] maven_compile_after_text_replacement

**Logged**: 2026-06-27T12:54:00+08:00
**Priority**: medium
**Status**: resolved
**Area**: backend

### Summary
补充下载百分比时使用全局文本替换，误改字符串响应返回值，并让下载流 close 声明抛出过宽异常，导致 Maven 编译失败。

### Error
```
UpdateService.java: 对资源变量 download 隐式调用 close() 时抛出未声明异常 java.lang.Exception
GeyserDownloadClient.java: String 无法转换为 InputStream
```

### Context
- Command/operation attempted: mvn -q -DskipTests package
- Related change: 为下载进度添加 Content-Length 与百分比输出。

### Suggested Fix
避免粗粒度全局替换；手动检查被替换方法。让 DownloadStream.close() 只抛 IOException，并只在 openDownload 方法中返回 DownloadStream。

### Metadata
- Reproducible: yes
- Related Files: src/main/java/com/xigua/geyserupdate/common/GeyserDownloadClient.java, src/main/java/com/xigua/geyserupdate/common/DownloadStream.java

### Resolution
- **Resolved**: 2026-06-27T12:56:00+08:00
- **Notes**: 已修复 sendString 返回 response.body()，DownloadStream.close() 改为只抛 IOException。

---

## [ERR-20260627-002] powershell_here_string_syntax

**Logged**: 2026-06-27T12:55:00+08:00
**Priority**: low
**Status**: resolved
**Area**: infra

### Summary
创建学习日志时将 PowerShell here-string 头和内容放在同一行，触发解析错误。

### Error
```
No characters are allowed after a here-string header but before the end of the line.
```

### Context
- Command/operation attempted: 初始化 .learnings 日志文件。

### Suggested Fix
PowerShell here-string 的 @' 或 @\" 必须单独占一行，内容从下一行开始。

### Metadata
- Reproducible: yes
- Related Files: .learnings/ERRORS.md

### Resolution
- **Resolved**: 2026-06-27T12:56:00+08:00
- **Notes**: 已改用正确 here-string 格式。

---
## [ERR-20260627-003] windows_loaded_plugin_jar_access_denied

**Logged**: 2026-06-27T13:16:00+08:00
**Priority**: high
**Status**: resolved
**Area**: backend

### Summary
在 Windows 服务端运行中直接覆盖已加载的 Geyser/Floodgate JAR，会因 Java/服务端持有文件锁而触发 AccessDeniedException。

### Error
```
java.nio.file.AccessDeniedException: plugins\GeyserUpdate\geyser-spigot.download -> plugins\Geyser-Spigot.jar
```

### Context
- 用户反馈 Paper/Leaves 运行时替换 Geyser-Spigot.jar 失败。
- 原实现下载校验后立即 Files.move 到 plugins 目录目标 JAR。

### Suggested Fix
不要在服务端运行中覆盖已加载插件 JAR。改为下载校验到 pending 目录，启动独立 Java 离线更新器等待当前服务端进程退出，再替换目标 JAR；随后执行 stop/end 由面板或守护进程拉起。

### Metadata
- Reproducible: yes
- Related Files: src/main/java/com/xigua/geyserupdate/common/UpdateService.java, src/main/java/com/xigua/geyserupdate/common/ExternalUpdateApplier.java

### Resolution
- **Resolved**: 2026-06-27T13:16:00+08:00
- **Notes**: 已发布 1.0.2，采用重启后离线替换模式。

---
## [ERR-20260627-004] partial_method_signature_update

**Logged**: 2026-06-27T13:37:00+08:00
**Priority**: medium
**Status**: resolved
**Area**: backend

### Summary
首次修改只更新了调用点，未成功更新 UpdateService 的双参数方法签名，导致 Maven 编译失败。

### Error
```
无法将方法 checkAndUpdate 应用到给定类型；需要 String，找到 String, boolean
```

### Context
- Command/operation attempted: mvn -q -DskipTests package
- 目标：区分每日定时检测与手动/启动检测的重启策略。

### Suggested Fix
对方法块做边界明确的整体替换，并立即用 ripgrep 验证签名和调用点。

### Metadata
- Reproducible: yes
- Related Files: src/main/java/com/xigua/geyserupdate/common/UpdateService.java

### Resolution
- **Resolved**: 2026-06-27T13:37:00+08:00
- **Notes**: 已精确替换方法块并构建通过。

---
## [ERR-20260702-001] github_raw_timeout

**Logged**: 2026-07-02T23:13:16.5327966+08:00
**Priority**: low
**Status**: pending
**Area**: infra

### Summary
读取 GitHub raw plugin.yml 时超时，未阻塞本地代码修改。

### Error
``
Invoke-WebRequest 超过 20 秒超时。
``

### Context
- 尝试读取 GeyserMC/Geyser 的 raw plugin.yml 以核对插件名。
- GitHub API 树请求可用，但 raw 内容请求超时。

### Suggested Fix
优先使用 GitHub contents API 或本地依赖信息核对元数据，避免依赖 raw.githubusercontent.com 的单次请求。

### Metadata
- Reproducible: unknown
- Related Files: src/main/java/com/xigua/geyserupdate/common/PluginProject.java

---
## [ERR-20260702-002] maven_build_timeout

**Logged**: 2026-07-02T23:23:00.3934810+08:00
**Priority**: medium
**Status**: pending
**Area**: infra

### Summary
首次 Maven 构建在 120 秒超时限制内未完成。

### Error
``
mvn -q clean package 超过 120 秒超时。
``

### Context
- 构建项目：GeyserUpdate
- 可能原因：首次解析或下载 Maven 依赖耗时较长。

### Suggested Fix
使用更长超时时间重新运行 Maven 构建，并在必要时取消 -q 以观察下载或编译进度。

### Metadata
- Reproducible: unknown
- Related Files: pom.xml

---
## [ERR-20260702-003] bungeecord_api_dependency_missing

**Logged**: 2026-07-02T23:23:50.8412315+08:00
**Priority**: high
**Status**: pending
**Area**: config

### Summary
Maven 无法解析 
et.md-5:bungeecord-api:1.21-R0.1-SNAPSHOT。

### Error
``
Could not find artifact net.md-5:bungeecord-api:jar:1.21-R0.1-SNAPSHOT in papermc-repo or sonatype groups/public.
``

### Context
- 命令：mvn -U clean package
- 当前仓库：PaperMC maven-public、Sonatype groups/public
- 影响：阻塞构建验证与产物更新。

### Suggested Fix
查询官方 BungeeCord Maven 仓库元数据，改为可解析的仓库或 API 版本，并重新构建验证。

### Metadata
- Reproducible: yes
- Related Files: pom.xml

---
## [ERR-20260703-001] powershell_python_heredoc

**Logged**: 2026-07-03T00:00:00+08:00
**Priority**: low
**Status**: pending
**Area**: infra

### Summary
在 PowerShell 中误用了 Bash 风格的 python - <<'PY' here-doc 语法，导致解析失败。

### Error
``text
ParserError: Missing file specification after redirection operator.
``

### Context
- 尝试检查 Python Pillow 是否可用。
- 当前 shell 是 PowerShell，不支持 Bash here-doc 重定向写法。

### Suggested Fix
在 PowerShell 中改用 python -c "..."，或使用 here-string 配合管道：@' ... '@ | python -。

### Metadata
- Reproducible: yes
- Related Files: 无

---
## [ERR-20260724-001] jshell_metadata_regression_harness

**Logged**: 2026-07-24T18:00:00+08:00
**Priority**: medium
**Status**: resolved
**Area**: tests

### Summary
实时更新客户端回归脚本通过 PowerShell 管道调用 JShell 时没有产生输出并以状态码 1 退出。

### Error
```
JShell 回归命令无标准输出，退出状态码：1。
```

### Context
- 操作：使用已构建的 `target/classes` 调用 GeyserDownloadClient，检查 Paper 与 BC 官方元数据。
- 可能原因：PowerShell here-string 与 JShell 输入管道的组合方式不兼容，尚未证明是插件代码错误。

### Suggested Fix
先单独验证 JShell 的最小输入，再使用明确的 Java 临时测试入口或分步命令执行回归。

### Metadata
- Reproducible: unknown
- Related Files: src/main/java/com/xigua/geyserupdate/common/GeyserDownloadClient.java

### Resolution
- **Resolved**: 2026-07-24T18:05:00+08:00
- **Notes**: 改用 PowerShell 数组逐行输入 JShell 并显式发送 `/exit`，Paper 与 BC 实时元数据回归检查已通过。

---
## [ERR-20260724-002] scheduler_regression_harness_setup

**Logged**: 2026-07-24T18:10:00+08:00
**Priority**: low
**Status**: resolved
**Area**: tests

### Summary
定时器专项 JShell 测试脚本误引用了不存在的临时配置类型，测试未执行到插件逻辑。

### Error
```
找不到符号：类 SimpleYamlConfigForTest
```

### Context
- 操作：验证定时器重载后旧任务不会再次调度。
- 原因：测试脚本占位类型未替换为项目实际的 SimpleYamlConfig 配置加载流程。

### Suggested Fix
使用 `target/classes/config.yml` 与项目实际配置类创建 UpdateConfig 后重跑。

### Metadata
- Reproducible: yes
- Related Files: src/main/java/com/xigua/geyserupdate/common/DailyUpdateScheduler.java

### Resolution
- **Resolved**: 2026-07-24T18:10:00+08:00
- **Notes**: 已修正测试脚本，使用真实配置路径回归验证了重载会取消旧任务，停止后旧回调不会再次调度。

---
