package com.jiajunxie.convert;

import com.jiajunxie.entity.Category;
import com.jiajunxie.vo.CategoryVo;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CategoryConvertServiceMapper {
  CategoryVo toVo(Category source);

  List<CategoryVo> toVos(List<Category> source);

  @Mapping(target = "parentKey", ignore = true)
  @Mapping(target = "parentCategory", ignore = true)
  Category toEntity(CategoryVo source);

  @Mapping(target = "categoryId", ignore = true)
  @Mapping(target = "parentKey", ignore = true)
  @Mapping(target = "parentCategory", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  void updateEntity(CategoryVo source, @MappingTarget Category target);
}
