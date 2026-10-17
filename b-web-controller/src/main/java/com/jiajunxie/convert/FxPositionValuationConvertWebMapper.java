package com.jiajunxie.convert;

import com.jiajunxie.response.FxPositionValuationResponse;
import com.jiajunxie.vo.FxPositionValuationVo;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface FxPositionValuationConvertWebMapper {
  FxPositionValuationResponse toResponse(FxPositionValuationVo source);
}
