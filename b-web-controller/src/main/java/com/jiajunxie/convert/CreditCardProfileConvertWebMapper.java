package com.jiajunxie.convert;

import com.jiajunxie.request.CreditCardProfileRequest;
import com.jiajunxie.request.CreditPaymentRuleRequest;
import com.jiajunxie.response.CreditCardDatePreviewResponse;
import com.jiajunxie.response.CreditCardProfileResponse;
import com.jiajunxie.response.CreditPaymentRuleResponse;
import com.jiajunxie.vo.CreditCardDatePreviewVo;
import com.jiajunxie.vo.CreditCardProfileVo;
import com.jiajunxie.vo.CreditPaymentRuleVo;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CreditCardProfileConvertWebMapper extends WebMapperSupport {
  @Mapping(target = "accountId", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "rules", ignore = true)
  @Mapping(target = "invalidDayRuleCode", qualifiedByName = "upperTrim")
  @Mapping(target = "businessDayAdjustmentCode", qualifiedByName = "upperTrim")
  @Mapping(target = "calendarCode", qualifiedByName = "emptyToNull")
  @Mapping(target = "timezone", qualifiedByName = "trim")
  @Mapping(target = "remainderRuleCode", qualifiedByName = "upperTrim")
  CreditCardProfileVo toVo(CreditCardProfileRequest request);

  @Mapping(target = "ruleId", ignore = true)
  @Mapping(target = "accountId", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "ruleTypeCode", qualifiedByName = "upperTrim")
  @Mapping(target = "purchaseStartMonthDay", qualifiedByName = "trim")
  @Mapping(target = "purchaseEndMonthDay", qualifiedByName = "trim")
  CreditPaymentRuleVo toRuleVo(CreditPaymentRuleRequest request);
  List<CreditPaymentRuleVo> toRuleVos(List<CreditPaymentRuleRequest> request);

  CreditCardProfileResponse toResponse(CreditCardProfileVo source);
  CreditPaymentRuleResponse toRuleResponse(CreditPaymentRuleVo source);
  List<CreditPaymentRuleResponse> toRuleResponses(List<CreditPaymentRuleVo> source);
  CreditCardDatePreviewResponse toPreviewResponse(CreditCardDatePreviewVo source);
}
