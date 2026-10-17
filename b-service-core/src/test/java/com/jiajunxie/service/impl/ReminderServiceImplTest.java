package com.jiajunxie.service.impl;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.jiajunxie.entity.Reminder;
import com.jiajunxie.mapper.AccountMapper;
import com.jiajunxie.mapper.ReminderMapper;
import com.jiajunxie.service.BarkNotificationService;
import com.jiajunxie.service.ExchangeRateAlertService;
import com.jiajunxie.vo.ReminderVo;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class ReminderServiceImplTest {

  @Test
  void monthlyDayMustStayWithinOneToThirtyOne() {
    ReminderMapper reminderMapper = mock(ReminderMapper.class);
    AccountMapper accountMapper = mock(AccountMapper.class);
    BarkNotificationService bark = mock(BarkNotificationService.class);
    ExchangeRateAlertService alertSettings = mock(ExchangeRateAlertService.class);
    ReminderServiceImpl service =
        new ReminderServiceImpl(reminderMapper, accountMapper, bark, alertSettings, mock(ReminderDateCalculator.class));

    ReminderVo invalid =
        new ReminderVo(
            null,
            "还款日",
            "CREDIT_CARD_PAYMENT",
            null,
            "MONTHLY",
            null,
            32,
            LocalTime.of(9, 0),
            "Asia/Tokyo",
            "CUSTOM",
            "LAST_DAY",
            null,
            "NONE",
            null,
            null,
            null,
            true,
            null,
            null,
            null);

    assertThrows(RuntimeException.class, () -> service.create(invalid));
    verify(reminderMapper, never()).insert(any(Reminder.class));
  }

  @Test
  void oneTimeReminderRequiresDate() {
    ReminderMapper reminderMapper = mock(ReminderMapper.class);
    ReminderServiceImpl service =
        new ReminderServiceImpl(
            reminderMapper,
            mock(AccountMapper.class),
            mock(BarkNotificationService.class),
            mock(ExchangeRateAlertService.class),
            mock(ReminderDateCalculator.class));

    ReminderVo invalid =
        new ReminderVo(
            null,
            "一次提醒",
            "CUSTOM",
            null,
            "ONCE",
            null,
            null,
            LocalTime.of(9, 0),
            "Asia/Tokyo",
            "CUSTOM",
            "LAST_DAY",
            null,
            "NONE",
            null,
            null,
            null,
            true,
            null,
            null,
            null);

    assertThrows(RuntimeException.class, () -> service.create(invalid));
    verify(reminderMapper, never()).insert(any(Reminder.class));
  }
}
