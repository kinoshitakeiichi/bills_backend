package com.jiajunxie.service;

import com.jiajunxie.vo.BarkNotificationVo;

public interface ForexMonitorService {

  void monitorUsdJpy();

  void sendPostNotification(BarkNotificationVo vo);
}
