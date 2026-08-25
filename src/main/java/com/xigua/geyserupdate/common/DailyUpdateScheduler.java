package com.xigua.geyserupdate.common;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public final class DailyUpdateScheduler {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final PluginLogger logger;
    private final UpdateConfig config;
    private final SchedulerAdapter scheduler;
    private final Runnable updateTask;
    private Object scheduledTask;
    private boolean started;

    public DailyUpdateScheduler(PluginLogger logger, UpdateConfig config, SchedulerAdapter scheduler, Runnable updateTask) {
        this.logger = logger;
        this.config = config;
        this.scheduler = scheduler;
        this.updateTask = updateTask;
    }

    public synchronized void start() {
        stop();
        started = true;
        scheduleNext();
    }

    public synchronized void stop() {
        started = false;
        if (scheduledTask != null) {
            scheduler.cancel(scheduledTask);
            scheduledTask = null;
        }
    }

    private synchronized void scheduleNext() {
        if (!started) {
            return;
        }
        long delayMillis = nextDelayMillis(config.dailyCheckTime());
        logger.info("下一次自动检测更新将在 " + formatDuration(delayMillis) + " 后执行，配置时间：" + config.dailyCheckTime());
        scheduledTask = scheduler.runLater(() -> {
            try {
                updateTask.run();
            } finally {
                scheduleNext();
            }
        }, delayMillis);
    }

    private long nextDelayMillis(String value) {
        LocalTime time;
        try {
            time = LocalTime.parse(value, TIME_FORMAT);
        } catch (DateTimeParseException e) {
            logger.warn("配置项 daily-check-time 格式无效：" + value + "，已使用默认时间 04:00。 ");
            time = LocalTime.of(4, 0);
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = now.withHour(time.getHour()).withMinute(time.getMinute()).withSecond(0).withNano(0);
        if (!next.isAfter(now)) {
            next = next.plusDays(1);
        }
        return Math.max(1000L, Duration.between(now, next).toMillis());
    }

    private static String formatDuration(long millis) {
        long totalSeconds = millis / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return hours + " 小时 " + minutes + " 分 " + seconds + " 秒";
    }

    public interface SchedulerAdapter {
        Object runLater(Runnable runnable, long delayMillis);

        void cancel(Object task);
    }
}
