# GeyserUpdate

GeyserUpdate 是一个面向 Paper 与 BungeeCord/Waterfall 的 Java 插件，用于检查并更新 Geyser、Floodgate 及对应平台上的其他插件。它从官方更新服务读取最新构建信息，下载文件并校验 SHA-256；更新文件会先放入插件数据目录，再由独立 Java 进程等服务端退出后替换。

## 支持范围

- Paper：检查已加载的 Geyser、Floodgate，并检查 ViaVersion、ViaBackwards、ViaRewind、ViaRewind-Legacy-Support 的 Paper Snapshot。
- BungeeCord/Waterfall：检查已加载的 Geyser、Floodgate，并检查 SkinsRestorer 的 Waterfall Release。
- Geyser 与 Floodgate 通过 Geyser 下载服务获取构建；Via 系列与 SkinsRestorer 通过 Hangar 获取构建。
- Java 编译目标为 17。Maven 使用 Paper API 1.20.4 与 BungeeCord API 1.21 作为编译依赖。

Paper 上的 Via 系列和 BungeeCord/Waterfall 上的 SkinsRestorer 会参与检查，即使对应插件当前没有加载；目标文件不存在时，更新流程会下载并安装该文件。请先确认这符合服务器的插件清单。

## 构建与安装

需要 JDK 17 和已安装的 Maven。在项目根目录运行：

```bash
mvn package
```

构建产物为 `target/GeyserUpdate-1.0.10.jar`。将该 JAR 放入 Paper 或 BungeeCord/Waterfall 的 `plugins/` 目录，然后启动服务器。打包资源同时包含 Paper 使用的 `plugin.yml` 和代理端使用的 `bungee.yml`。

首次启动会在 `plugins/GeyserUpdate/` 下生成 `config.yml`。编辑配置后使用 `/geyserupdate reload` 重新加载。

## 命令与权限

| 命令 | 作用 |
| --- | --- |
| `/geyserupdate check` | 立即异步检查更新，并在控制台显示进度 |
| `/geyserupdate reload` | 重新读取配置并重启每日定时任务 |
| `/geyserupdate status` | 查看运行状态、平台、每日检查时间和关闭命令 |

所有命令需要 `geyserupdate.admin` 权限，默认授予管理员（OP）。BungeeCord/Waterfall 还提供别名 `/guupdate`。

## 更新流程与重启行为

1. 插件按平台筛选项目，并查询最新构建。Geyser 与 Floodgate 只在服务器已加载相应插件时检查；Paper 的 Via 系列和代理端的 SkinsRestorer 始终纳入检查。
2. 插件比较本地文件与官方 SHA-256。需要更新时，下载到 `plugins/GeyserUpdate/` 并校验后，放入 `pending/`。
3. 插件启动离线更新器。更新器等待当前服务器进程退出，再替换目标 JAR，并清理同一官方项目旧命名的 JAR。
4. 启动时检查和手动 `/geyserupdate check` 不会自动关闭服务器。管理员需要安排一次重启，离线更新器会在进程退出后完成替换。
5. 每日定时检查在发现更新后，默认会执行配置的关闭命令：Paper 为 `stop`，BungeeCord/Waterfall 为 `end`。将 `auto-shutdown-after-update` 设为 `false` 可关闭这项自动关服行为；更新文件仍会等待管理员重启后替换。

自动更新会覆盖目标插件 JAR，并删除代码识别为同一官方项目的旧版 JAR。启用前请备份 `plugins/`，并确认维护窗口与关闭命令适合当前服务器。

## 配置

配置文件位置：`plugins/GeyserUpdate/config.yml`。

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `check-on-start` | `true` | 插件启动后检查一次；不会自动关服 |
| `daily-check-time` | `04:00` | 每日检查时间，使用服务器进程所在系统的本地时区，格式为 `HH:mm` |
| `paper-shutdown-command` | `stop` | Paper 每日检查发现更新后执行的控制台命令 |
| `bungee-shutdown-command` | `end` | BungeeCord/Waterfall 每日检查发现更新后执行的控制台命令 |
| `auto-shutdown-after-update` | `true` | 是否在每日检查下载并校验完更新后执行关闭命令 |
| `connect-timeout-millis` | `15000` | 网络连接超时，毫秒 |
| `read-timeout-millis` | `30000` | 网络请求读取超时，毫秒 |
| `progress-log-interval-millis` | `1000` | 下载进度日志间隔，毫秒 |

配置文件还包含 `notify-console-only`，但当前加载代码没有读取该项；修改它不会改变提示行为。管理命令的结果会回复给命令发送者，下载进度和更新细节写入控制台日志。

## 运行文件

- `plugins/GeyserUpdate/config.yml`：运行配置。
- `plugins/GeyserUpdate/installed.properties`：最近检查到的版本、构建号、文件、校验值和状态。
- `plugins/GeyserUpdate/pending/`：已下载并校验、等待服务端退出后替换的文件及任务清单。
- `plugins/GeyserUpdate/offline-update.log`：离线替换器的执行记录。

如果服务器已重启但插件仍未更新，先检查 `offline-update.log` 和 `pending/`，确认替换任务完成情况及目标文件路径。

## 项目结构

- `src/main/java/.../paper/`：Paper 插件入口、命令和日志适配。
- `src/main/java/.../bungee/`：BungeeCord/Waterfall 插件入口、命令和日志适配。
- `src/main/java/.../common/`：更新服务、平台与插件项目定义、网络下载、配置读取和离线替换。
- `src/main/resources/plugin.yml`、`bungee.yml`：两种平台的插件描述文件。
- `src/main/resources/config.yml`：首次启动时复制的默认配置。

## 当前元数据说明

当前 `pom.xml` 版本为 `1.0.10`，Paper 的 `plugin.yml` 与代理端的 `bungee.yml` 版本仍为 `1.0.9`。构建文件名取自 `pom.xml`，插件加载器显示的版本取自各自描述文件；发布前应统一这几个版本值。
