package com.jiajunxie.vo;

public record CurrencyInventoryRebuildIssueVo(
    String transactionId,
    String accountId,
    String code,
    String message) {}
