package com.jiajunxie.vo;

import java.util.List;

public record FxLegacyRebuildRequestVo(
    List<String> accountIds,
    List<String> openingBalanceTransactionIds,
    Integer openingRateLookAheadDays) {}
