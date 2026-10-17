package com.jiajunxie.convert;

import com.jiajunxie.response.ExchangeRateResponse;
import com.jiajunxie.response.RealtimeExchangeRateResponse;
import com.jiajunxie.vo.ExchangeRateVo;
import com.jiajunxie.vo.RealtimeExchangeRateVo;
import java.util.List;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ExchangeRateConvertWebMapper {
  ExchangeRateResponse toResponse(ExchangeRateVo source);
  List<ExchangeRateResponse> toResponses(List<ExchangeRateVo> source);
  RealtimeExchangeRateResponse toRealtimeResponse(RealtimeExchangeRateVo source);
}
