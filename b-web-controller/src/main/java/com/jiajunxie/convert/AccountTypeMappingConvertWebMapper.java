package com.jiajunxie.convert;

import com.jiajunxie.response.AccountTypeMappingResponse;
import com.jiajunxie.response.AccountTypeOptionResponse;
import com.jiajunxie.vo.AccountTypeMappingVo;
import com.jiajunxie.vo.AccountTypeOptionVo;
import java.util.List;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AccountTypeMappingConvertWebMapper {
  List<AccountTypeMappingResponse> toResponses(List<AccountTypeMappingVo> source);
  List<AccountTypeOptionResponse> toOptionResponses(List<AccountTypeOptionVo> source);
}
