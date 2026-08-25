# Learnings

Corrections, insights, and knowledge gaps captured during development.

**Categories**: correction | insight | knowledge_gap | best_practice

---
## [LRN-20260627-001] correction

**Logged**: 2026-06-27T13:37:00+08:00
**Priority**: high
**Status**: resolved
**Area**: backend

### Summary
自动更新插件不应在启动检测或管理员手动检测下载完成后立即重启。

### Details
用户明确要求更新触发重启只能发生在每日凌晨 4 点定时检测；其他场景只下载、校验并准备离线替换，等待管理员手动重启。

### Suggested Action
更新入口需要区分检测来源：每日定时检测允许自动关闭，启动检测和手动命令检测禁止自动关闭。

### Metadata
- Source: user_feedback
- Related Files: src/main/java/com/xigua/geyserupdate/common/UpdateService.java, src/main/java/com/xigua/geyserupdate/paper/GeyserUpdatePaperPlugin.java, src/main/java/com/xigua/geyserupdate/bungee/GeyserUpdateBungeePlugin.java
- Tags: minecraft-plugin, auto-update, restart-policy

### Resolution
- **Resolved**: 2026-06-27T13:37:00+08:00
- **Notes**: 已发布 1.0.3，仅每日定时检测调用 checkAndUpdate(reason, true)。

---
