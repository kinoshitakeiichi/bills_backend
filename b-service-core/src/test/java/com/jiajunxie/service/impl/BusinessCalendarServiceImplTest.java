package com.jiajunxie.service.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jiajunxie.convert.BusinessCalendarConvertServiceMapper;
import com.jiajunxie.entity.BusinessCalendar;
import com.jiajunxie.entity.BusinessCalendarDay;
import com.jiajunxie.mapper.BusinessCalendarDayMapper;
import com.jiajunxie.mapper.BusinessCalendarMapper;
import com.jiajunxie.mapper.CreditCardProfileMapper;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class BusinessCalendarServiceImplTest {
  @Test
  void explicitDayOverridesWeekendDefault() {
    BusinessCalendarMapper calendarMapper = mock(BusinessCalendarMapper.class);
    BusinessCalendarDayMapper dayMapper = mock(BusinessCalendarDayMapper.class);
    BusinessCalendar calendar = new BusinessCalendar();
    calendar.setCalendarId("calendar");
    calendar.setCalendarCode("JP_CARD");
    when(calendarMapper.selectOne(any())).thenReturn(calendar);

    BusinessCalendarDay override = new BusinessCalendarDay();
    override.setBusinessDay(true);
    when(dayMapper.selectOne(any())).thenReturn(override);

    var service = new BusinessCalendarServiceImpl(
        calendarMapper, dayMapper, mock(CreditCardProfileMapper.class),
        mock(BusinessCalendarConvertServiceMapper.class));

    assertTrue(service.isBusinessDay("JP_CARD", LocalDate.of(2026, 8, 9)));
  }

  @Test
  void weekendIsNonBusinessWithoutCalendarOverride() {
    var service = new BusinessCalendarServiceImpl(
        mock(BusinessCalendarMapper.class), mock(BusinessCalendarDayMapper.class),
        mock(CreditCardProfileMapper.class), mock(BusinessCalendarConvertServiceMapper.class));
    assertFalse(service.isBusinessDay(null, LocalDate.of(2026, 8, 9)));
  }
}
