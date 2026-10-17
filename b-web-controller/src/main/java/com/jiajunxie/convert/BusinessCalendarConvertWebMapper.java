package com.jiajunxie.convert;

import com.jiajunxie.request.BusinessCalendarDayRequest;
import com.jiajunxie.request.BusinessCalendarRequest;
import com.jiajunxie.response.BusinessCalendarDayResponse;
import com.jiajunxie.response.BusinessCalendarResponse;
import com.jiajunxie.response.SelectOptionResponse;
import com.jiajunxie.vo.BaseOptionVo;
import com.jiajunxie.vo.BusinessCalendarDayVo;
import com.jiajunxie.vo.BusinessCalendarVo;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface BusinessCalendarConvertWebMapper extends WebMapperSupport {
  @Mapping(target = "calendarId", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "calendarCode", qualifiedByName = "upperTrim")
  @Mapping(target = "calendarName", qualifiedByName = "trim")
  @Mapping(target = "countryCode", qualifiedByName = "emptyToNull")
  BusinessCalendarVo toVo(BusinessCalendarRequest request);

  @Mapping(target = "calendarDayId", ignore = true)
  @Mapping(target = "calendarId", ignore = true)
  @Mapping(target = "calendarDate", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "holidayName", qualifiedByName = "emptyToNull")
  @Mapping(target = "source", qualifiedByName = "emptyToNull")
  BusinessCalendarDayVo toDayVo(BusinessCalendarDayRequest request);

  BusinessCalendarResponse toResponse(BusinessCalendarVo source);
  List<BusinessCalendarResponse> toResponses(List<BusinessCalendarVo> source);
  BusinessCalendarDayResponse toDayResponse(BusinessCalendarDayVo source);
  List<BusinessCalendarDayResponse> toDayResponses(List<BusinessCalendarDayVo> source);
  List<SelectOptionResponse> toOptionResponses(List<BaseOptionVo> source);
}
