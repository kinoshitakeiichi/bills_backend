package com.jiajunxie.mapper;

import com.jiajunxie.domain.inventory.CurrencyInventoryAccountSnapshot;
import com.jiajunxie.domain.inventory.CurrencyInventoryReplayRow;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** Facts, cutover locks and audit metadata used by FX Domain V3 reconstruction. */
@Mapper
public interface CurrencyInventoryRebuildMapper {

  /** Serialize cutover Apply against normal ASSET-account writes before recalculating Preview. */
  @Select(
      """
      SELECT account_id
      FROM bills.b_accounts
      WHERE UPPER(account_class_code) = 'ASSET'
        AND UPPER(account_type_code) <> 'PERSONAL_ACCOUNT'
      ORDER BY account_id
      FOR UPDATE
      """)
  List<String> lockAssetAccountsForUpdate();

  @Select("SELECT count(*) FROM bills.b_currency_inventory_buckets")
  long countInventoryBuckets();

  @Select("SELECT count(*) FROM bills.b_currency_inventory_movements")
  long countInventoryMovements();

  @Select("SELECT count(*) FROM bills.b_currency_inventory_cutover_runs")
  long countInventoryCutoverRuns();

  @Select("SELECT max(applied_at) FROM bills.b_currency_inventory_cutover_runs")
  Instant selectInventoryCutoverAppliedAt();

  @Insert(
      """
      INSERT INTO bills.b_currency_inventory_cutover_runs(
        cutover_id, fingerprint, applied_at, replay_transaction_count, replay_account_count,
        bucket_count, movement_count
      ) VALUES (
        #{cutoverId}, #{fingerprint}, #{appliedAt}, #{replayTransactionCount}, #{replayAccountCount},
        #{bucketCount}, #{movementCount}
      )
      """)
  int insertInventoryCutoverRun(
      @Param("cutoverId") String cutoverId,
      @Param("fingerprint") String fingerprint,
      @Param("appliedAt") Instant appliedAt,
      @Param("replayTransactionCount") int replayTransactionCount,
      @Param("replayAccountCount") int replayAccountCount,
      @Param("bucketCount") int bucketCount,
      @Param("movementCount") int movementCount);

  @Select(
      """
      SELECT
        a.account_id AS account_id,
        a.account_name AS account_name,
        UPPER(a.currency) AS currency,
        a.total_amount AS account_balance,
        COALESCE(SUM(e.amount), 0) AS ledger_balance
      FROM bills.b_accounts a
      LEFT JOIN bills.b_transaction_entries e ON e.account_id = a.account_id
      WHERE UPPER(a.account_class_code) = 'ASSET'
        AND UPPER(a.account_type_code) <> 'PERSONAL_ACCOUNT'
      GROUP BY a.account_id, a.account_name, a.currency, a.total_amount
      ORDER BY a.created_at, a.account_id
      """)
  List<CurrencyInventoryAccountSnapshot> selectAssetAccounts();

  /**
   * Accounts whose current legacy V2 state actually contains a positive FX Position.
   *
   * <p>V3 historical cutover uses this only as an eligibility marker. Position quantity/cost is
   * deliberately not copied because legacy positions may include non-FX interest/opening amounts.
   */
  @Select(
      """
      SELECT DISTINCT p.account_id
      FROM bills.b_fx_positions p
      JOIN bills.b_accounts a ON a.account_id = p.account_id
      WHERE UPPER(a.account_class_code) = 'ASSET'
        AND UPPER(a.account_type_code) <> 'PERSONAL_ACCOUNT'
        AND p.quantity > 0
        AND UPPER(p.currency) = UPPER(a.currency)
      ORDER BY p.account_id
      """)
  List<String> selectLegacyFxPositionAccountIds();

  /**
   * The FX-cost preview needs ledger deltas and real EXCHANGE facts only. Category, legacy opening
   * markers and full non-FX provenance are intentionally outside this query's responsibility.
   */
  @Select(
      """
      SELECT
        t.transaction_id AS transaction_id,
        UPPER(t.transaction_type_code) AS transaction_type,
        t.transaction_time AS transaction_time,
        t.created_at AS transaction_created_at,
        e.entry_id AS entry_id,
        e.account_id AS account_id,
        e.amount AS amount,
        UPPER(e.currency) AS entry_currency,
        UPPER(e.entry_role_code) AS entry_role,
        e.created_at AS entry_created_at,
        a.account_name AS account_name,
        UPPER(a.currency) AS account_currency
      FROM bills.b_transactions t
      JOIN bills.b_transaction_entries e ON e.transaction_id = t.transaction_id
      JOIN bills.b_accounts a ON a.account_id = e.account_id
      WHERE UPPER(a.account_class_code) = 'ASSET'
        AND UPPER(a.account_type_code) <> 'PERSONAL_ACCOUNT'
      ORDER BY
        t.transaction_time,
        t.created_at,
        t.transaction_id,
        e.created_at,
        e.entry_id
      """)
  List<CurrencyInventoryReplayRow> selectAssetReplayRows();
}
