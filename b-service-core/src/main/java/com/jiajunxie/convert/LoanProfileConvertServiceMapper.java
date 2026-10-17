package com.jiajunxie.convert;

import com.jiajunxie.entity.LoanProfile;
import com.jiajunxie.entity.LoanRateHistory;
import com.jiajunxie.vo.LoanProfileVo;
import com.jiajunxie.vo.LoanRateHistoryVo;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface LoanProfileConvertServiceMapper {
  LoanProfileVo toVo(LoanProfile source);
  LoanProfile toEntity(LoanProfileVo source);
  void updateEntity(LoanProfileVo source, @MappingTarget LoanProfile target);
  LoanRateHistoryVo toRateVo(LoanRateHistory source);
  List<LoanRateHistoryVo> toRateVos(List<LoanRateHistory> source);
  LoanRateHistory toRateEntity(LoanRateHistoryVo source);
}
