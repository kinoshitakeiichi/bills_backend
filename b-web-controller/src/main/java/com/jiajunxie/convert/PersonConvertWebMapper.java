package com.jiajunxie.convert;

import com.jiajunxie.request.PersonRequest;
import com.jiajunxie.response.PersonResponse;
import com.jiajunxie.response.SelectOptionResponse;
import com.jiajunxie.vo.PersonOptionVo;
import com.jiajunxie.vo.PersonVo;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PersonConvertWebMapper extends WebMapperSupport {
  @Mapping(target = "personId", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  PersonVo toVo(PersonRequest source);

  PersonResponse toResponse(PersonVo source);

  List<PersonResponse> toResponses(List<PersonVo> source);

  List<SelectOptionResponse> toOptionResponses(List<PersonOptionVo> source);
}
