package com.jiajunxie.response;

import tools.jackson.databind.JsonNode;

public record AccountTransactionTemplateResponse(
    String templateId,
    String accountId,
    String templateName,
    JsonNode preset,
    Integer sortOrder,
    Boolean enabled) {}
