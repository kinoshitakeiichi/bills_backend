package com.jiajunxie.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** 主数据管理列表的统一分页请求。 */
public record MasterDataPageRequest(
    @Min(1) Integer page,
    @Min(1) @Max(200) Integer size,
    String keyword,
    String sortField,
    String sortOrder) {}
