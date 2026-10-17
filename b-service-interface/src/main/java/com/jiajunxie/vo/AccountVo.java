package com.jiajunxie.vo;

public record AccountVo(
    String accountId,
    String accountName,
    String accountCode,
    String issuer,
    String accountClassCode,
    String accountTypeCode,
    String currency,
    String ownerPersonId,
    String parentAccountId,
    String autoDebitAccountId) {}
