package com.jiajunxie.mapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.time.Instant;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class TransactionMapperFxDetailContractTest {

  @Test
  void accountHistoryProjectsFxDetailForReadonlyExpansion() throws Exception {
    Method method =
        TransactionMapper.class.getMethod(
            "selectAccountTransactions",
            String.class,
            Instant.class,
            Instant.class,
            String.class);

    Select select = method.getAnnotation(Select.class);
    String sql = String.join("\n", select.value());

    assertTrue(sql.contains("LEFT JOIN bills.b_fx_details fx"));
    assertTrue(sql.contains("fx.fx_direction_code AS fxDirectionCode"));
    assertTrue(sql.contains("source_account.account_name AS fxSourceAccountName"));
    assertTrue(sql.contains("target_account.account_name AS fxTargetAccountName"));
    assertTrue(sql.contains("fx.executed_rate AS fxExecutedRate"));
    assertTrue(sql.contains("fx.fee_amount AS fxFeeAmount"));
    assertTrue(sql.contains("fx.cost_basis_amount AS fxCostBasisAmount"));
    assertTrue(sql.contains("fx.realized_gain_loss AS fxRealizedGainLoss"));
  }
}
