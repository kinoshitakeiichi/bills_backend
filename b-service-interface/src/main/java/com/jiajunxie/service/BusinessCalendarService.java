package com.jiajunxie.service;

import com.jiajunxie.vo.BusinessCalendarDayVo;
import com.jiajunxie.vo.BusinessCalendarVo;
import com.jiajunxie.vo.BaseOptionVo;
import java.time.LocalDate;
import java.util.List;

public interface BusinessCalendarService {
  List<BusinessCalendarVo> list(String keyword, Boolean enabled);
  List<BaseOptionVo> options();
  int create(BusinessCalendarVo vo);
  int update(String calendarId, BusinessCalendarVo vo);
  int setEnabled(String calendarId, boolean enabled);
  List<BusinessCalendarDayVo> listDays(String calendarId, LocalDate dateFrom, LocalDate dateTo);
  int upsertDay(String calendarId, LocalDate date, BusinessCalendarDayVo vo);
  int removeDay(String calendarId, LocalDate date);
  boolean isBusinessDay(String calendarCode, LocalDate date);
}
