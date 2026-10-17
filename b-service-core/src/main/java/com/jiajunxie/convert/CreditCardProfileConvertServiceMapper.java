package com.jiajunxie.convert;

import com.jiajunxie.entity.CreditCardProfile;
import com.jiajunxie.entity.CreditPaymentRule;
import com.jiajunxie.vo.CreditCardProfileVo;
import com.jiajunxie.vo.CreditPaymentRuleVo;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface CreditCardProfileConvertServiceMapper {
  @Mapping(target = "rules", ignore = true)
  CreditCardProfileVo toVo(CreditCardProfile source);
  CreditCardProfile toEntity(CreditCardProfileVo source);
  void updateEntity(CreditCardProfileVo source, @MappingTarget CreditCardProfile target);
  CreditPaymentRuleVo toRuleVo(CreditPaymentRule source);
  List<CreditPaymentRuleVo> toRuleVos(List<CreditPaymentRule> source);
  CreditPaymentRule toRuleEntity(CreditPaymentRuleVo source);
  void updateRuleEntity(CreditPaymentRuleVo source, @MappingTarget CreditPaymentRule target);
}
