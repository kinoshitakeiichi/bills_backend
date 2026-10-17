package com.jiajunxie.convert;

import com.jiajunxie.request.ExchangeRateAlertRuleRequest;
import com.jiajunxie.request.ExchangeRateAlertSettingsRequest;
import com.jiajunxie.response.ExchangeRateAlertRuleResponse;
import com.jiajunxie.response.ExchangeRateAlertSettingsResponse;
import com.jiajunxie.vo.ExchangeRateAlertRuleVo;
import com.jiajunxie.vo.ExchangeRateAlertSettingsVo;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ExchangeRateAlertConvertWebMapper extends WebMapperSupport {

  @Mapping(target = "barkDeviceKeyConfigured", constant = "false")
  @Mapping(target = "globalNotificationTitle", qualifiedByName = "emptyToNull")
  @Mapping(target = "globalNotificationBodyTemplate", qualifiedByName = "emptyToNull")
  @Mapping(target = "globalNotificationUrl", qualifiedByName = "emptyToNull")
  @Mapping(target = "barkSound", qualifiedByName = "emptyToNull")
  @Mapping(target = "barkGroupName", qualifiedByName = "emptyToNull")
  @Mapping(target = "barkLevel", qualifiedByName = "emptyToNull")
  ExchangeRateAlertSettingsVo toSettingsVo(ExchangeRateAlertSettingsRequest source);

  ExchangeRateAlertSettingsResponse toSettingsResponse(ExchangeRateAlertSettingsVo source);

  @Mapping(target = "alertRuleId", ignore = true)
  @Mapping(target = "lastObservedRate", ignore = true)
  @Mapping(target = "lastTriggeredAt", ignore = true)
  @Mapping(target = "rateSourceCode", qualifiedByName = "emptyToNull")
  @Mapping(target = "channelCode", qualifiedByName = "emptyToNull")
  @Mapping(target = "notificationTitle", qualifiedByName = "emptyToNull")
  @Mapping(target = "notificationBodyTemplate", qualifiedByName = "emptyToNull")
  @Mapping(target = "notificationUrl", qualifiedByName = "emptyToNull")
  @Mapping(target = "barkLevel", qualifiedByName = "emptyToNull")
  @Mapping(target = "barkSound", qualifiedByName = "emptyToNull")
  ExchangeRateAlertRuleVo toRuleVo(ExchangeRateAlertRuleRequest source);

  ExchangeRateAlertRuleResponse toRuleResponse(ExchangeRateAlertRuleVo source);

  List<ExchangeRateAlertRuleResponse> toRuleResponses(List<ExchangeRateAlertRuleVo> source);
}
