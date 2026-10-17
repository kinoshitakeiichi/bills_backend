package com.jiajunxie.mapper;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jiajunxie.entity.Transaction;
import com.jiajunxie.model.AccountTransactionRow;

@Mapper
public interface TransactionMapper extends BaseMapper<Transaction> {

  @Select(
      """
      <script>
      SELECT
          t.transaction_id AS transactionId,
          t.transaction_type_code AS transactionType,
          transaction_type_item.item_name AS transactionTypeName,
          t.category_id AS categoryId,
          c.category_name AS categoryName,
          t.merchant AS merchant,
          t.remark AS remark,
          t.transaction_time AS transactionTime,
          e.amount AS amount,
          e.currency AS currency,
          e.entry_role_code AS entryRole,
          t.origin_code AS originCode,
          t.origin_reference_id AS originReferenceId,
          fx.fx_direction_code AS fxDirectionCode,
          fx.source_account_id AS fxSourceAccountId,
          source_account.account_name AS fxSourceAccountName,
          fx.source_amount AS fxSourceAmount,
          fx.source_currency AS fxSourceCurrency,
          fx.target_account_id AS fxTargetAccountId,
          target_account.account_name AS fxTargetAccountName,
          fx.target_amount AS fxTargetAmount,
          fx.target_currency AS fxTargetCurrency,
          fx.executed_rate AS fxExecutedRate,
          fx.fee_amount AS fxFeeAmount,
          fx.fee_currency AS fxFeeCurrency,
          fx.cost_basis_amount AS fxCostBasisAmount,
          fx.realized_gain_loss AS fxRealizedGainLoss,
          fx.gain_loss_currency AS fxGainLossCurrency,
          loan_payment.principal_amount AS "loanPrincipalAmount",
          loan_payment.interest_amount AS "loanInterestAmount",
          loan_payment.fee_amount AS "loanFeeAmount",
          CASE
            WHEN loan_payment.transaction_id IS NULL THEN NULL
            ELSE loan_payment.principal_amount + loan_payment.interest_amount + loan_payment.fee_amount
          END AS "loanTotalPaymentAmount",
          loan_source_entry.account_id AS "loanSourceAccountId",
          loan_source_account.account_name AS "loanSourceAccountName"
      FROM bills.b_transaction_entries e
      INNER JOIN bills.b_transactions t
          ON t.transaction_id = e.transaction_id
      LEFT JOIN bills.b_categories c
          ON c.category_id = t.category_id
      LEFT JOIN bills.b_fx_details fx
          ON fx.transaction_id = t.transaction_id
      LEFT JOIN bills.b_loan_payment_details loan_payment
          ON loan_payment.transaction_id = t.transaction_id
      LEFT JOIN bills.b_transaction_entries loan_source_entry
          ON loan_source_entry.transaction_id = t.transaction_id
         AND loan_source_entry.entry_role_code = 'SOURCE'
         AND t.transaction_type_code = 'LOAN_PAYMENT'
      LEFT JOIN bills.b_accounts loan_source_account
          ON loan_source_account.account_id = loan_source_entry.account_id
      LEFT JOIN bills.b_accounts source_account
          ON source_account.account_id = fx.source_account_id
      LEFT JOIN bills.b_accounts target_account
          ON target_account.account_id = fx.target_account_id
      LEFT JOIN bills.b_dictionary_types transaction_type_dict
          ON transaction_type_dict.type_code = 'TRANSACTION_TYPE'
      LEFT JOIN bills.b_dictionary_items transaction_type_item
          ON transaction_type_item.dictionary_type_id = transaction_type_dict.dictionary_type_id
         AND transaction_type_item.item_code = t.transaction_type_code
      WHERE e.account_id = #{accountId}
        AND t.transaction_time &gt;= #{startInclusive}
        AND t.transaction_time &lt; #{endExclusive}
      <if test="keyword != null and keyword != ''">
        AND (
          COALESCE(t.merchant, '') ILIKE CONCAT('%', #{keyword}, '%')
          OR COALESCE(t.remark, '') ILIKE CONCAT('%', #{keyword}, '%')
          OR COALESCE(c.category_name, '') ILIKE CONCAT('%', #{keyword}, '%')
          OR COALESCE(transaction_type_item.item_name, '') ILIKE CONCAT('%', #{keyword}, '%')
          OR COALESCE(t.transaction_type_code, '') ILIKE CONCAT('%', #{keyword}, '%')
          OR CAST(e.amount AS TEXT) ILIKE CONCAT('%', #{keyword}, '%')
        )
      </if>
      ORDER BY t.transaction_time DESC, t.transaction_id DESC
      </script>
      """)
  List<AccountTransactionRow> selectAccountTransactions(
      @Param("accountId") String accountId,
      @Param("startInclusive") Instant startInclusive,
      @Param("endExclusive") Instant endExclusive,
      @Param("keyword") String keyword);


  @Select(
      """
      SELECT transaction_id
      FROM bills.b_transactions
      WHERE transaction_time >= #{startInclusive}
        AND transaction_time < #{endExclusive}
      ORDER BY transaction_time, transaction_id
      """)
  List<String> selectBudgetCandidateIds(
      @Param("startInclusive") Instant startInclusive,
      @Param("endExclusive") Instant endExclusive);


  @Select(
      """
      SELECT transaction_id
      FROM bills.b_transactions
      WHERE transaction_time >= #{startInclusive}
        AND transaction_time < #{endExclusive}
      ORDER BY transaction_time, transaction_id
      """)
  List<String> selectAnalyticsCandidateIds(
      @Param("startInclusive") Instant startInclusive,
      @Param("endExclusive") Instant endExclusive);


  @Select(
      """
      SELECT COUNT(1)
      FROM bills.b_transactions
      WHERE origin_reference_id = #{transactionId}
        AND transaction_type_code IN ('REFUND', 'CREDIT_REFUND')
      """)
  long countRefundReferences(@Param("transactionId") String transactionId);
}
