package com.jiajunxie.vo;

import java.time.Instant;
import java.util.List;

public record CurrencyInventoryRebuildPreviewVo(
    Instant generatedAt,
    String fingerprint,
    int replayTransactionCount,
    int replayAccountCount,
    int globalIssueCount,
    boolean applyEligible,
    List<CurrencyInventoryAccountPreviewVo> accounts,
    List<CurrencyInventoryRebuildIssueVo> globalIssues) {}
