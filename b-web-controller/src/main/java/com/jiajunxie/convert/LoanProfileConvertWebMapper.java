package com.jiajunxie.convert;

import com.jiajunxie.request.LoanProfileRequest;
import com.jiajunxie.request.LoanRateHistoryRequest;
import com.jiajunxie.response.LoanProfileResponse;
import com.jiajunxie.response.LoanRateHistoryResponse;
import com.jiajunxie.vo.LoanProfileVo;
import com.jiajunxie.vo.LoanRateHistoryVo;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface LoanProfileConvertWebMapper extends WebMapperSupport {
  @Mapping(target = "accountId", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "loanTypeCode", qualifiedByName = "upperTrim")
  @Mapping(target = "fundingModeCode", qualifiedByName = "upperTrim")
  @Mapping(target = "currency", qualifiedByName = "emptyToNull")
  @Mapping(target = "rateTypeCode", qualifiedByName = "upperTrim")
  @Mapping(target = "paymentFrequencyCode", qualifiedByName = "upperTrim")
  @Mapping(target = "calendarCode", qualifiedByName = "emptyToNull")
  @Mapping(target = "businessDayAdjustmentCode", qualifiedByName = "emptyToNull")
  LoanProfileVo toVo(LoanProfileRequest request);

  @Mapping(target = "rateHistoryId", ignore = true)
  @Mapping(target = "accountId", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  LoanRateHistoryVo toRateVo(LoanRateHistoryRequest request);

  LoanProfileResponse toResponse(LoanProfileVo source);
  LoanRateHistoryResponse toRateResponse(LoanRateHistoryVo source);
  List<LoanRateHistoryResponse> toRateResponses(List<LoanRateHistoryVo> source);
}
