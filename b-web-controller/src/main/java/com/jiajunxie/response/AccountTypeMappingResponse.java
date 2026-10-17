package com.jiajunxie.response;

public record AccountTypeMappingResponse(
    String accountTypeItemId,
    String accountTypeCode,
    String accountTypeName,
    String accountClassItemId,
    String accountClassCode,
    String accountClassName) {}
