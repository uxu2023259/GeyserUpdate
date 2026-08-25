# GeyserUpdate MineBBS 发布资料

## 标题
GeyserUpdate｜Geyser / Floodgate / Via / SkinsRestorer 自动更新器

## 短描述
Paper/BC 双端自动更新 Geyser、Floodgate、Via 全家桶与 SkinsRestorer，支持定时检测、SHA-256 校验和重启后离线替换。

## 插件介绍

### 插件简介
GeyserUpdate 是一款面向互通服与跨版本服的自动更新插件，用来减少服务器管理员反复手动下载、替换 Geyser、Floodgate、Via 全家桶和 SkinsRestorer 的维护成本。

插件会根据当前运行平台自动选择对应项目，连接公开更新接口获取最新构建，下载后进行 SHA-256 校验，并在需要替换已加载插件文件时启动离线更新器。对于 Windows 等会锁定插件 JAR 的环境，离线更新器会等待服务器进程退出后再替换文件，确保重启后就是新版本。

### 支持平台
- Paper：用于更新 Geyser-Spigot、floodgate-spigot，以及 Via 全家桶。
- BungeeCord / Waterfall：用于更新 Geyser-BungeeCord、floodgate-bungee，以及 SkinsRestorer。
- 运行环境：建议 Java 17 或更高版本。

### 自动更新项目

#### Paper 端
- Geyser-Spigot
- floodgate-spigot
- ViaVersion
- ViaBackwards
- ViaRewind
- ViaRewind-Legacy-Support

说明：Paper 端 Via 全家桶使用 Hangar 的 Snapshot 通道，适合希望及时跟进跨版本组件更新的服务器。

#### BC / Waterfall 端
- Geyser-BungeeCord
- floodgate-bungee
- SkinsRestorer

### 核心功能
- 启动后自动检测：可在配置中控制是否启用。
- 每日定时检测：默认每天 04:00 检查，可自定义时间。
- 手动检测更新：管理员可随时执行命令触发异步检测。
- 下载进度输出：控制台显示下载百分比、大小与速度。
- SHA-256 文件校验：下载完成后校验文件完整性，校验失败不会替换原文件。
- 重启后离线替换：先下载到待更新目录，服务器关闭后再替换目标插件文件。
- 防重复任务：已有检测任务运行时会自动跳过新的重复请求。
- 配置热重载：修改配置后可直接使用命令重新加载。

### 命令说明
| 命令 | 说明 | 权限 |
| --- | --- | --- |
| `/geyserupdate check` | 立即异步检测更新 | `geyserupdate.admin` |
| `/geyserupdate reload` | 重新加载配置文件 | `geyserupdate.admin` |
| `/geyserupdate status` | 查看当前检测/更新状态 | `geyserupdate.admin` |

BC / Waterfall 端额外支持别名：`/guupdate`。

### 权限节点
```text
geyserupdate.admin
```
Paper 端默认仅 OP 可使用；BC / Waterfall 端需要为管理员授予该权限。

### 安装方式
1. 将 `GeyserUpdate` 插件 JAR 放入服务端 `plugins` 目录。
2. 重启服务器，等待生成配置文件。
3. 按需修改 `plugins/GeyserUpdate/config.yml`。
4. 使用 `/geyserupdate check` 手动测试一次更新检测。
5. 如检测到更新，插件会下载并校验文件；管理员在合适时间重启服务器后，新版本会自动替换完成。

### 配置说明
常用配置项如下：

```yaml
# 是否在插件启动后自动检测一次更新
check-on-start: true

# 每天自动检测更新时间，格式为 HH:mm
daily-check-time: "04:00"

# Paper 端更新完成后的关闭命令
paper-shutdown-command: "stop"

# BC / Waterfall 端更新完成后的关闭命令
bungee-shutdown-command: "end"

# 是否允许每日定时检测更新完成后自动执行关闭命令
auto-shutdown-after-update: true
```

注意：启动检测和 `/geyserupdate check` 手动检测不会自动关闭服务器；只有每日定时检测，并且 `auto-shutdown-after-update` 为 `true` 时，才会在更新准备完成后执行对应关闭命令。

### 适合谁使用？
- 使用 Geyser + Floodgate 搭建基岩版互通的服务器。
- Paper 端需要保持 ViaVersion / ViaBackwards / ViaRewind 等组件更新的服务器。
- BC / Waterfall 端同时维护 Geyser、Floodgate、SkinsRestorer 的群组服。
- 希望减少手动下载插件、核对文件、停服替换等重复维护工作的管理员。

### 温馨提示
- 本插件会访问公开更新接口下载插件文件，请确保服务器网络可以正常连接相关站点。
- 建议在正式服使用前先在测试环境验证一次更新流程。
- 自动更新不能替代备份，建议定期备份 `plugins` 目录和服务端核心数据。
- Via 全家桶更新面向 Paper 端；SkinsRestorer 更新面向 BC / Waterfall 端。

## 图标文件
- 高清主图：`C:\Users\hyx\Desktop\GeyserUpdate\MineBBS发布资料\GeyserUpdate_MineBBS_Icon.png`
- 512 版：`C:\Users\hyx\Desktop\GeyserUpdate\MineBBS发布资料\GeyserUpdate_MineBBS_Icon_512.png`
- 256 版：`C:\Users\hyx\Desktop\GeyserUpdate\MineBBS发布资料\GeyserUpdate_MineBBS_Icon_256.png`

## 图标设计说明
图标采用 Minecraft 风格的像素方块背景，叠加蓝绿色发光氛围与科技感圆环，中间使用现代发光的 “G” 字母，适合作为 GeyserUpdate 在 MineBBS 的插件资源图标。

