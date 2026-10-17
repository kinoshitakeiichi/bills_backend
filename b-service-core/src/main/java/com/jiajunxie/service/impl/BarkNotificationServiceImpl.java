package com.jiajunxie.service.impl;

import com.jiajunxie.service.BarkNotificationService;
import com.jiajunxie.vo.BarkNotificationVo;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
public class BarkNotificationServiceImpl implements BarkNotificationService {

  private final String deviceKey;
  private final RestTemplate restTemplate = new RestTemplate();

  public BarkNotificationServiceImpl(@Value("${bark.device-key:}") String deviceKey) {
    this.deviceKey = deviceKey == null ? "" : deviceKey.trim();
  }

  @Override
  public boolean isConfigured() {
    return StringUtils.hasText(deviceKey);
  }

  @Override
  public boolean send(String baseUrl, BarkNotificationVo notification) {
    if (notification == null) return false;
    if (!isConfigured()) {
      log.warn("Bark Device Key 未配置，跳过通知发送");
      return false;
    }
    if (!StringUtils.hasText(baseUrl)) {
      log.warn("Bark Base URL 未配置，跳过通知发送");
      return false;
    }

    try {
      Map<String, Object> payload = notification.toPayload();
      payload.put("device_key", deviceKey);

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      restTemplate.postForObject(
          baseUrl.trim(), new HttpEntity<>(payload, headers), String.class);
      return true;
    } catch (RuntimeException ex) {
      log.error("Bark 通知发送失败: {}", ex.getMessage(), ex);
      return false;
    }
  }
}
