package com.jiajunxie.vo;

import java.math.BigDecimal;

/**
 * Read model used by account card/list views.
 *
 * <p>{@code ownerPersonId} remains the stable relationship key used by edit forms, while
 * {@code ownerPersonName} is the human-readable display value. Account class/type names are also
 * resolved from master data so business views do not need to render internal codes or IDs.
 */
public record AccountInfoVo(
    String accountId,
    String accountName,
    String accountCode,
    String issuer,
    String accountClassCode,
    String accountClassName,
    String accountTypeCode,
    String accountTypeName,
    BigDecimal totalAmount,
    String currency,
    String ownerPersonId,
    String ownerPersonName,
    String parentAccountId,
    String autoDebitAccountId,
    Boolean enabled) {}
