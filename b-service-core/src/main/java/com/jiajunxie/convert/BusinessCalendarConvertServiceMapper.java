package com.jiajunxie.convert;

import com.jiajunxie.entity.BusinessCalendar;
import com.jiajunxie.entity.BusinessCalendarDay;
import com.jiajunxie.vo.BusinessCalendarDayVo;
import com.jiajunxie.vo.BusinessCalendarVo;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface BusinessCalendarConvertServiceMapper {
  BusinessCalendarVo toVo(BusinessCalendar source);
  List<BusinessCalendarVo> toVos(List<BusinessCalendar> source);
  BusinessCalendarDayVo toDayVo(BusinessCalendarDay source);
  List<BusinessCalendarDayVo> toDayVos(List<BusinessCalendarDay> source);
  BusinessCalendar toEntity(BusinessCalendarVo source);
  BusinessCalendarDay toDayEntity(BusinessCalendarDayVo source);
  void updateEntity(BusinessCalendarVo source, @MappingTarget BusinessCalendar target);
  void updateDayEntity(BusinessCalendarDayVo source, @MappingTarget BusinessCalendarDay target);
}
