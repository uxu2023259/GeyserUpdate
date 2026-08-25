package com.xigua.geyserupdate.bungee;

import com.xigua.geyserupdate.common.DailyUpdateScheduler;
import com.xigua.geyserupdate.common.ServerPlatform;
import com.xigua.geyserupdate.common.SimpleYamlConfig;
import com.xigua.geyserupdate.common.UpdateConfig;
import com.xigua.geyserupdate.common.UpdateService;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.TabExecutor;
import net.md_5.bungee.api.scheduler.ScheduledTask;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public final class GeyserUpdateBungeePlugin extends Plugin {
    private BungeePluginLogger pluginLogger;
    private SimpleYamlConfig yamlConfig;
    private UpdateConfig updateConfig;
    private UpdateService updateService;
    private DailyUpdateScheduler scheduler;
    private GeyserUpdateCommand command;

    @Override
    public void onEnable() {
        pluginLogger = new BungeePluginLogger(getLogger());
        if (!reloadInternal()) {
            pluginLogger.error("插件启动失败，请检查配置文件。 ");
            return;
        }

        command = new GeyserUpdateCommand();
        ProxyServer.getInstance().getPluginManager().registerCommand(this, command);
        scheduler.start();
        if (updateConfig.checkOnStart()) {
            runAsyncCheck("插件启动检测");
        }
        pluginLogger.info("GeyserUpdate 已启用，当前端类型：BC/Waterfall。 ");
    }

    @Override
    public void onDisable() {
        if (scheduler != null) {
            scheduler.stop();
        }
        if (command != null) {
            ProxyServer.getInstance().getPluginManager().unregisterCommand(command);
        }
        pluginLogger.info("GeyserUpdate 已卸载。 ");
    }

    private boolean reloadInternal() {
        try {
            if (pluginLogger == null) {
                pluginLogger = new BungeePluginLogger(getLogger());
            }
            DailyUpdateScheduler previousScheduler = scheduler;
            try (InputStream defaultConfig = getResourceAsStream("config.yml")) {
                yamlConfig = SimpleYamlConfig.load(getDataFolder().toPath(), defaultConfig);
            }
            updateConfig = UpdateConfig.from(yamlConfig, ServerPlatform.BUNGEE);
            Path pluginsFolder = getDataFolder().toPath().getParent();
            if (pluginsFolder == null) {
                pluginsFolder = Path.of("plugins");
            }
            updateService = new UpdateService(
                    ServerPlatform.BUNGEE,
                    pluginsFolder,
                    getDataFolder().toPath(),
                    pluginLogger,
                    updateConfig,
                    this::loadedPluginNames,
                    () -> ProxyServer.getInstance().getScheduler().runAsync(this, () -> ProxyServer.getInstance().getPluginManager().dispatchCommand(ProxyServer.getInstance().getConsole(), updateConfig.shutdownCommand()))
            );
            DailyUpdateScheduler newScheduler = new DailyUpdateScheduler(pluginLogger, updateConfig, new DailyUpdateScheduler.SchedulerAdapter() {
                @Override
                public Object runLater(Runnable runnable, long delayMillis) {
                    return ProxyServer.getInstance().getScheduler().schedule(GeyserUpdateBungeePlugin.this, runnable, delayMillis, TimeUnit.MILLISECONDS);
                }

                @Override
                public void cancel(Object task) {
                    if (task instanceof ScheduledTask scheduledTask) {
                        scheduledTask.cancel();
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
        ProxyServer.getInstance().getScheduler().runAsync(this, () -> updateService.checkAndUpdate(reason));
    }

    private Set<String> loadedPluginNames() {
        Set<String> names = new LinkedHashSet<>();
        for (Plugin plugin : ProxyServer.getInstance().getPluginManager().getPlugins()) {
            names.add(plugin.getDescription().getName());
        }
        return names;
    }

    private final class GeyserUpdateCommand extends Command implements TabExecutor {
        private GeyserUpdateCommand() {
            super("geyserupdate", "geyserupdate.admin", "guupdate");
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (args.length == 0) {
                sendHelp(sender);
                return;
            }

            String sub = args[0].toLowerCase(Locale.ROOT);
            switch (sub) {
                case "check" -> {
                    sender.sendMessage("§a已开始异步检测 Geyser/Floodgate/SkinsRestorer 插件更新，请查看控制台输出。 ");
                    runAsyncCheck("命令手动检测");
                }
                case "reload" -> {
                    if (reloadInternal()) {
                        scheduler.start();
                        sender.sendMessage("§a配置已重新加载。 ");
                    } else {
                        sender.sendMessage("§c配置重新加载失败，请查看控制台错误。 ");
                    }
                }
                case "status" -> {
                    sender.sendMessage("§aGeyserUpdate 状态：" + (updateService != null && updateService.isRunning() ? "正在检测或更新" : "空闲"));
                    sender.sendMessage("§a当前端类型：BC/Waterfall");
                    sender.sendMessage("§a每日检测时间：" + updateConfig.dailyCheckTime());
                    sender.sendMessage("§a更新后关闭命令：" + updateConfig.shutdownCommand());
                }
                default -> sendHelp(sender);
            }
        }

        @Override
        public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
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

        private void sendHelp(CommandSender sender) {
            sender.sendMessage("§6GeyserUpdate 命令帮助：");
            sender.sendMessage("§e/geyserupdate check §7- 立即异步检测更新");
            sender.sendMessage("§e/geyserupdate reload §7- 重新加载配置");
            sender.sendMessage("§e/geyserupdate status §7- 查看当前状态");
        }
    }
}


