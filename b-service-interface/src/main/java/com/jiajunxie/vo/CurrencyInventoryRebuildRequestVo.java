package com.jiajunxie.vo;

import java.util.List;

/**
 * Empty/null accountIds means return the preview for every ASSET account.
 *
 * <p>When accountIds is supplied, the response issue/apply scope is limited to the requested
 * accounts plus FX/TRANSFER dependency accounts that can actually carry cost into or out of them.
 */
public record CurrencyInventoryRebuildRequestVo(List<String> accountIds) {}
