package com.jiajunxie.service.impl;

import com.jiajunxie.service.BarkNotificationService;
import com.jiajunxie.service.ExchangeRateAlertService;
import com.jiajunxie.service.ForexMonitorService;
import com.jiajunxie.service.TradingViewRateProvider;
import com.jiajunxie.vo.BarkNotificationVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 旧 ForexMonitor 兼容入口。
 *
 * <p>F6-PR004 起，定时汇率提醒由 ExchangeRateAlertScheduler + ExchangeRateAlertService
 * 负责。本类不再保存 USDJPY、阈值、Bark Key 或固定调度配置。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ForexMonitorServiceImpl implements ForexMonitorService {

  private final TradingViewRateProvider tradingViewRateProvider;
  private final ExchangeRateAlertService alertService;
  private final BarkNotificationService barkNotificationService;

  @Override
  public void monitorUsdJpy() {
    var quote = tradingViewRateProvider.getQuote("USD", "JPY");
    log.info("兼容 ForexMonitor 查询 USD/JPY：{}", quote.exchangeRate());
  }

  @Override
  public void sendPostNotification(BarkNotificationVo vo) {
    if (vo == null) return;
    var settings = alertService.getSettings();
    barkNotificationService.send(settings.barkBaseUrl(), vo);
  }
}
