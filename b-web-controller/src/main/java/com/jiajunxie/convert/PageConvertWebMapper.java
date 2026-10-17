package com.jiajunxie.convert;

import com.jiajunxie.dto.PageQuery;
import com.jiajunxie.request.MasterDataPageRequest;
import org.mapstruct.Mapper;

/** Web 分页请求到 Service 契约的转换；不包含排序白名单等业务规则。 */
@Mapper(componentModel = "spring")
public interface PageConvertWebMapper extends WebMapperSupport {
  PageQuery toQuery(MasterDataPageRequest source);
}
