package com.jiajunxie.vo;

public record AccountOptionVo(
    String value,
    String label,
    String accountClassCode,
    String accountTypeCode,
    String currency,
    String ownerPersonId,
    Boolean baseAccount) {}
