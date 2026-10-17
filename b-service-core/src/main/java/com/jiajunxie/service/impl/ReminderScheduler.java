package com.jiajunxie.service.impl;

import com.jiajunxie.service.ReminderService;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.scheduler.reminder", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class ReminderScheduler {

  private final ReminderService reminderService;
  private ThreadPoolTaskScheduler scheduler;
  private ScheduledFuture<?> future;

  @EventListener(ApplicationReadyEvent.class)
  public void start() {
    scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(1);
    scheduler.setThreadNamePrefix("reminder-");
    scheduler.initialize();
    future = scheduler.scheduleWithFixedDelay(this::runSafely, Duration.ofMinutes(1));
  }

  private void runSafely() {
    try {
      reminderService.evaluateDueReminders();
    } catch (RuntimeException ex) {
      log.error("自定义提醒轮询失败", ex);
    }
  }

  @PreDestroy
  public void shutdown() {
    if (future != null) future.cancel(false);
    if (scheduler != null) scheduler.shutdown();
  }
}
