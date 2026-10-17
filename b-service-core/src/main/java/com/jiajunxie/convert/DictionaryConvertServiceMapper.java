package com.jiajunxie.convert;

import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import com.jiajunxie.entity.DictionaryItem;
import com.jiajunxie.entity.DictionaryType;
import com.jiajunxie.vo.DictionaryItemVo;
import com.jiajunxie.vo.DictionaryTypeVo;

@Mapper(componentModel = "spring")
public interface DictionaryConvertServiceMapper extends MapperSupport {

  @Mapping(target = "itemCount", ignore = true)
  DictionaryTypeVo toTypeVo(DictionaryType entity);

  List<DictionaryTypeVo> toTypeVos(List<DictionaryType> entities);

  @Mapping(target = "dictionaryTypeId", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  DictionaryType toTypeEntity(DictionaryTypeVo vo);

  @Mapping(target = "dictionaryTypeId", ignore = true)
  @Mapping(target = "typeCode", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  void updateTypeEntity(DictionaryTypeVo vo, @MappingTarget DictionaryType entity);

  @Mapping(target = "typeCode", ignore = true)
  DictionaryItemVo toItemVo(DictionaryItem entity);

  List<DictionaryItemVo> toItemVos(List<DictionaryItem> entities);

  @Mapping(target = "dictionaryItemId", ignore = true)
  @Mapping(target = "dictionaryTypeId", ignore = true)
  @Mapping(target = "systemItem", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "dictionaryType", ignore = true)
  DictionaryItem toItemEntity(DictionaryItemVo vo);

  @Mapping(target = "dictionaryItemId", ignore = true)
  @Mapping(target = "dictionaryTypeId", ignore = true)
  @Mapping(target = "systemItem", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "dictionaryType", ignore = true)
  void updateItemEntity(DictionaryItemVo vo, @MappingTarget DictionaryItem entity);
}
