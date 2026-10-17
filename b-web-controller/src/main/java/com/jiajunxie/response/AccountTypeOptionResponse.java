package com.jiajunxie.response;

public record AccountTypeOptionResponse(
    String value,
    String label,
    String accountClassCode,
    String accountClassLabel) {}
