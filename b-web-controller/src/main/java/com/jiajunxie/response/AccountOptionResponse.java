package com.jiajunxie.response;

public record AccountOptionResponse(
    String value,
    String label,
    String accountClassCode,
    String accountTypeCode,
    String currency,
    String ownerPersonId,
    Boolean baseAccount) {}
