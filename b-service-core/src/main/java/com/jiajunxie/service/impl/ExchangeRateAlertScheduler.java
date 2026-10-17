package com.jiajunxie.service.impl;

import com.jiajunxie.service.ExchangeRateAlertService;
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
@ConditionalOnProperty(prefix = "app.scheduler.fx-alert", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class ExchangeRateAlertScheduler {

  private final ExchangeRateAlertService alertService;

  private final Object monitor = new Object();
  private ThreadPoolTaskScheduler scheduler;
  private ScheduledFuture<?> future;

  @EventListener(ApplicationReadyEvent.class)
  public void start() {
    synchronized (monitor) {
      if (scheduler == null) {
        scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("fx-alert-");
        scheduler.initialize();
      }
    }
    reschedule(alertService.getSettings().pollingIntervalSeconds());
  }

  @EventListener
  public void onSettingsChanged(ExchangeRateAlertSettingsChangedEvent event) {
    reschedule(event.pollingIntervalSeconds());
  }

  public void reschedule(int pollingIntervalSeconds) {
    synchronized (monitor) {
      if (scheduler == null) return;
      if (future != null) future.cancel(false);
      future =
          scheduler.scheduleWithFixedDelay(
              this::runSafely, Duration.ofSeconds(pollingIntervalSeconds));
      log.info("FX 汇率提醒调度周期已更新为 {} 秒", pollingIntervalSeconds);
    }
  }

  private void runSafely() {
    try {
      alertService.evaluateEnabledRules();
    } catch (RuntimeException ex) {
      log.error("FX 汇率提醒轮询失败", ex);
    }
  }

  @PreDestroy
  public void shutdown() {
    synchronized (monitor) {
      if (future != null) future.cancel(false);
      if (scheduler != null) scheduler.shutdown();
    }
  }
}
