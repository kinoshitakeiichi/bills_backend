package com.jiajunxie.convert;

import com.jiajunxie.request.CategoryRequest;
import com.jiajunxie.response.CategoryResponse;
import com.jiajunxie.response.CategoryManagementTreeResponse;
import com.jiajunxie.response.CategoryTreeResponse;
import com.jiajunxie.response.SelectOptionResponse;
import com.jiajunxie.vo.CategoryOptionVo;
import com.jiajunxie.vo.CategoryManagementTreeVo;
import com.jiajunxie.vo.CategoryVo;
import com.jiajunxie.vo.CategoryTreeVo;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CategoryConvertWebMapper extends WebMapperSupport {
  @Mapping(target = "categoryId", ignore = true)
  @Mapping(target = "parentId", source = "parentId", qualifiedByName = "emptyToNull")
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  CategoryVo toVo(CategoryRequest source);

  CategoryResponse toResponse(CategoryVo source);

  List<CategoryResponse> toResponses(List<CategoryVo> source);

  List<SelectOptionResponse> toOptionResponses(List<CategoryOptionVo> source);

  List<CategoryTreeResponse> toTreeResponses(List<CategoryTreeVo> tree);

  List<CategoryManagementTreeResponse> toManagementTreeResponses(
      List<CategoryManagementTreeVo> tree);
}

