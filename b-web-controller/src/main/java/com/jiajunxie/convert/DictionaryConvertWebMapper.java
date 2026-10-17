package com.jiajunxie.convert;

import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import com.jiajunxie.request.DictionaryItemRequest;
import com.jiajunxie.request.DictionaryTypeRequest;
import com.jiajunxie.response.DictionaryItemResponse;
import com.jiajunxie.response.DictionaryOptionResponse;
import com.jiajunxie.response.DictionaryTypeResponse;
import com.jiajunxie.vo.DictionaryItemVo;
import com.jiajunxie.vo.DictionaryOptionVo;
import com.jiajunxie.vo.DictionaryTypeVo;

@Mapper(componentModel = "spring")
public interface DictionaryConvertWebMapper extends WebMapperSupport {
  @Mapping(target = "dictionaryTypeId", ignore = true)
  @Mapping(target = "itemCount", ignore = true)
  @Mapping(target = "description", source = "description", qualifiedByName = "emptyToNull")
  DictionaryTypeVo toTypeVo(DictionaryTypeRequest source);

  @Mapping(target = "dictionaryItemId", ignore = true)
  @Mapping(target = "dictionaryTypeId", ignore = true)
  @Mapping(target = "typeCode", ignore = true)
  @Mapping(target = "systemItem", ignore = true)
  @Mapping(target = "description", source = "description", qualifiedByName = "emptyToNull")
  @Mapping(target = "metadataJson", source = "metadataJson", qualifiedByName = "emptyToNull")
  DictionaryItemVo toItemVo(DictionaryItemRequest source);

  List<DictionaryTypeResponse> toTypeResponses(List<DictionaryTypeVo> source);

  DictionaryItemResponse toItemResponse(DictionaryItemVo source);

  List<DictionaryItemResponse> toItemResponses(List<DictionaryItemVo> source);

  List<DictionaryOptionResponse> toOptionResponses(List<DictionaryOptionVo> source);
}
