package com.jiajunxie.service.impl;

import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Defines the accounting cost currency used when an independent foreign-currency account receives
 * its first non-FX inflow (interest, salary, etc.) before any FxPosition exists.
 *
 * <p>This setting is intentionally independent from Account.parentAccountId. Parent/child is only an
 * organizational relationship; FX accounting uses this explicit currency policy plus the sticky
 * FxPosition.costCurrency once a position exists.
 */
@Component
public class FxAccountingPolicy {
  private final String defaultCostCurrency;

  public FxAccountingPolicy(
      @Value("${fx.accounting-cost-currency:JPY}") String defaultCostCurrency) {
    if (defaultCostCurrency == null || defaultCostCurrency.isBlank()) {
      throw new IllegalArgumentException("fx.accounting-cost-currency 不能为空");
    }
    this.defaultCostCurrency = defaultCostCurrency.trim().toUpperCase(Locale.ROOT);
  }

  public String defaultCostCurrency() {
    return defaultCostCurrency;
  }
}
