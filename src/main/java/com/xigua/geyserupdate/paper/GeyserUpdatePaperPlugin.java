package com.xigua.geyserupdate.paper;

import com.xigua.geyserupdate.common.DailyUpdateScheduler;
import com.xigua.geyserupdate.common.ServerPlatform;
import com.xigua.geyserupdate.common.SimpleYamlConfig;
import com.xigua.geyserupdate.common.UpdateConfig;
import com.xigua.geyserupdate.common.UpdateService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class GeyserUpdatePaperPlugin extends JavaPlugin implements TabExecutor {
    private PaperPluginLogger pluginLogger;
    private SimpleYamlConfig yamlConfig;
    private UpdateConfig updateConfig;
    private UpdateService updateService;
    private DailyUpdateScheduler scheduler;

    @Override
    public void onEnable() {
        pluginLogger = new PaperPluginLogger(getLogger());
        if (!reloadInternal()) {
            pluginLogger.error("插件启动失败，请检查配置文件。 ");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        if (getCommand("geyserupdate") != null) {
            getCommand("geyserupdate").setExecutor(this);
            getCommand("geyserupdate").setTabCompleter(this);
        }

        scheduler.start();
        if (updateConfig.checkOnStart()) {
            runAsyncCheck("插件启动检测");
        }
        pluginLogger.info("GeyserUpdate 已启用，当前端类型：Paper。 ");
    }

    @Override
    public void onDisable() {
        if (scheduler != null) {
            scheduler.stop();
        }
        pluginLogger.info("GeyserUpdate 已卸载。 ");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("geyserupdate.admin")) {
            sender.sendMessage("§c你没有权限使用此命令。 ");
            return true;
        }
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "check" -> {
                sender.sendMessage("§a已开始异步检测 Geyser/Floodgate 和 Via 全家桶更新，请查看控制台输出。 ");
                runAsyncCheck("命令手动检测");
                return true;
            }
            case "reload" -> {
                if (reloadInternal()) {
                    scheduler.start();
                    sender.sendMessage("§a配置已重新加载。 ");
                } else {
                    sender.sendMessage("§c配置重新加载失败，请查看控制台错误。 ");
                }
                return true;
            }
            case "status" -> {
                sender.sendMessage("§aGeyserUpdate 状态：" + (updateService != null && updateService.isRunning() ? "正在检测或更新" : "空闲"));
                sender.sendMessage("§a当前端类型：Paper");
                sender.sendMessage("§a每日检测时间：" + updateConfig.dailyCheckTime());
                sender.sendMessage("§a更新后关闭命令：" + updateConfig.shutdownCommand());
                return true;
            }
            default -> {
                sendHelp(sender);
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("geyserupdate.admin")) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> result = new ArrayList<>();
            for (String candidate : List.of("check", "reload", "status")) {
                if (candidate.startsWith(prefix)) {
                    result.add(candidate);
                }
            }
            return result;
        }
        return Collections.emptyList();
    }

    private boolean reloadInternal() {
        try {
            if (pluginLogger == null) {
                pluginLogger = new PaperPluginLogger(getLogger());
            }
            DailyUpdateScheduler previousScheduler = scheduler;
            try (InputStream defaultConfig = getResource("config.yml")) {
                yamlConfig = SimpleYamlConfig.load(getDataFolder().toPath(), defaultConfig);
            }
            updateConfig = UpdateConfig.from(yamlConfig, ServerPlatform.PAPER);
            Path pluginsFolder = getDataFolder().toPath().getParent();
            if (pluginsFolder == null) {
                pluginsFolder = Path.of("plugins");
            }
            updateService = new UpdateService(
                    ServerPlatform.PAPER,
                    pluginsFolder,
                    getDataFolder().toPath(),
                    pluginLogger,
                    updateConfig,
                    this::loadedPluginNames,
                    () -> Bukkit.getScheduler().runTask(this, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), updateConfig.shutdownCommand()))
            );
            DailyUpdateScheduler newScheduler = new DailyUpdateScheduler(pluginLogger, updateConfig, new DailyUpdateScheduler.SchedulerAdapter() {
                @Override
                public Object runLater(Runnable runnable, long delayMillis) {
                    long ticks = Math.max(1L, delayMillis / 50L);
                    return Bukkit.getScheduler().runTaskLaterAsynchronously(GeyserUpdatePaperPlugin.this, runnable, ticks);
                }

                @Override
                public void cancel(Object task) {
                    if (task instanceof BukkitTask bukkitTask) {
                        bukkitTask.cancel();
                    }
                }
            }, () -> updateService.checkAndUpdate("每日定时检测", true));
            if (previousScheduler != null) {
                previousScheduler.stop();
            }
            scheduler = newScheduler;
            return true;
        } catch (Exception e) {
            pluginLogger.error("加载配置失败。", e);
            return false;
        }
    }

    private void runAsyncCheck(String reason) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> updateService.checkAndUpdate(reason));
    }

    private Set<String> loadedPluginNames() {
        Set<String> names = new LinkedHashSet<>();
        for (org.bukkit.plugin.Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
            names.add(plugin.getName());
        }
        return names;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6GeyserUpdate 命令帮助：");
        sender.sendMessage("§e/geyserupdate check §7- 立即异步检测 Geyser/Floodgate 和 Via 全家桶更新");
        sender.sendMessage("§e/geyserupdate reload §7- 重新加载配置");
        sender.sendMessage("§e/geyserupdate status §7- 查看当前状态");
    }
}


