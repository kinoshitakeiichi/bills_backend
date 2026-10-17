package com.jiajunxie.vo;

public record AccountTransactionTemplateSaveVo(
    String templateName,
    String presetJson,
    Integer sortOrder,
    Boolean enabled) {}
