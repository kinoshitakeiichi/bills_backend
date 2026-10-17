package com.jiajunxie.service;

import com.jiajunxie.vo.BarkNotificationVo;

public interface BarkNotificationService {
  boolean isConfigured();
  boolean send(String baseUrl, BarkNotificationVo notification);
}
