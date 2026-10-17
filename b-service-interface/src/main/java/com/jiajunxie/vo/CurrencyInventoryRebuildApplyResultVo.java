package com.jiajunxie.vo;

import java.time.Instant;

public record CurrencyInventoryRebuildApplyResultVo(
    String cutoverId,
    Instant appliedAt,
    String fingerprint,
    int persistedBucketCount,
    int persistedMovementCount) {}
