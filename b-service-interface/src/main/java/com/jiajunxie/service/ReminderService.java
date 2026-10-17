package com.jiajunxie.service;

import com.jiajunxie.vo.ReminderDatePreviewVo;
import com.jiajunxie.vo.ReminderVo;
import java.time.LocalDate;
import java.util.List;

public interface ReminderService {
  List<ReminderVo> list(Boolean enabled);
  ReminderVo create(ReminderVo reminder);
  ReminderVo update(String reminderId, ReminderVo reminder);
  int setEnabled(String reminderId, boolean enabled);
  int delete(String reminderId);
  ReminderDatePreviewVo preview(ReminderVo reminder, LocalDate referenceDate);
  void evaluateDueReminders();
}
