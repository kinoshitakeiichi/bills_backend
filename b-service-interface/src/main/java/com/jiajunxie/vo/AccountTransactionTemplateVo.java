package com.jiajunxie.vo;

public record AccountTransactionTemplateVo(
    String templateId,
    String accountId,
    String templateName,
    String presetJson,
    Integer sortOrder,
    Boolean enabled) {}
