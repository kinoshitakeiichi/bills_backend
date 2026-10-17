package com.jiajunxie.request;

import tools.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AccountTransactionTemplateRequest(
    @NotBlank @Size(max = 100) String templateName,
    @NotNull JsonNode preset,
    Integer sortOrder,
    Boolean enabled) {}
