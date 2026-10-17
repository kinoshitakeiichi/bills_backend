package com.jiajunxie.mapper;

import com.jiajunxie.model.TransactionLedgerCandidate;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TransactionLedgerQueryMapper {

  @Select("""
      <script>
      SELECT
        t.transaction_id AS "transactionId",
        t.transaction_type_code AS "transactionTypeCode",
        di.item_name AS "transactionTypeName",
        t.category_id AS "categoryId",
        c.category_name AS "categoryName",
        t.merchant,
        t.remark,
        t.transaction_time AS "transactionTime",
        t.origin_code AS "originCode",
        t.origin_reference_id AS "originReferenceId",
        t.created_at AS "createdAt",
        fx.fx_direction_code AS "fxDirectionCode",
        fx.fee_amount AS "fxFeeAmount",
        fx.fee_currency AS "fxFeeCurrency"
      FROM bills.b_transactions t
      LEFT JOIN bills.b_categories c ON c.category_id = t.category_id
      LEFT JOIN bills.b_fx_details fx ON fx.transaction_id = t.transaction_id
      LEFT JOIN bills.b_dictionary_types dt ON dt.type_code = 'TRANSACTION_TYPE'
      LEFT JOIN bills.b_dictionary_items di
        ON di.dictionary_type_id = dt.dictionary_type_id
       AND di.item_code = t.transaction_type_code
      WHERE t.transaction_time &gt;= #{startInclusive}
        AND t.transaction_time &lt; #{endExclusive}
      <if test="accountId != null and accountId != ''">
        AND EXISTS (
          SELECT 1 FROM bills.b_transaction_entries e
          WHERE e.transaction_id = t.transaction_id AND e.account_id = #{accountId}
        )
      </if>
      <if test="accountTypeCode != null and accountTypeCode != ''">
        AND EXISTS (
          SELECT 1
          FROM bills.b_transaction_entries e
          JOIN bills.b_accounts a ON a.account_id = e.account_id
          WHERE e.transaction_id = t.transaction_id
            AND a.account_type_code = #{accountTypeCode}
        )
      </if>
      <if test="categoryId != null and categoryId != ''">
        AND t.category_id IN (
          WITH RECURSIVE category_tree AS (
            SELECT category_id
            FROM bills.b_categories
            WHERE category_id = #{categoryId}

            UNION ALL

            SELECT child.category_id
            FROM bills.b_categories child
            JOIN category_tree parent ON child.parent_id = parent.category_id
          )
          SELECT category_id FROM category_tree
        )
      </if>
      <if test="personIds != null and !personIds.isEmpty()">
        AND EXISTS (
          SELECT 1
          FROM bills.b_transaction_allocations al
          WHERE al.transaction_id = t.transaction_id
            AND al.person_id IN
            <foreach collection="personIds" item="personId" open="(" separator="," close=")">
              #{personId}
            </foreach>
        )
      </if>
      <if test="unassignedPerson">
        AND NOT EXISTS (
          SELECT 1
          FROM bills.b_transaction_allocations unassigned_al
          WHERE unassigned_al.transaction_id = t.transaction_id
        )
        AND t.transaction_type_code IN (
          'INCOME',
          'REFUND',
          'CREDIT_REFUND',
          'EXPENSE',
          'CREDIT_PURCHASE',
          'LOAN_PURCHASE'
        )
      </if>
      <if test="transactionTypeCode != null and transactionTypeCode != ''">
        AND t.transaction_type_code = #{transactionTypeCode}
      </if>
      <if test="keyword != null and keyword != '' and !keywordRegex">
        AND (
          COALESCE(t.merchant, '') ILIKE CONCAT('%', #{keyword}, '%')
          OR COALESCE(t.remark, '') ILIKE CONCAT('%', #{keyword}, '%')
          OR COALESCE(c.category_name, '') ILIKE CONCAT('%', #{keyword}, '%')
          OR COALESCE(di.item_name, '') ILIKE CONCAT('%', #{keyword}, '%')
          OR COALESCE(t.transaction_type_code, '') ILIKE CONCAT('%', #{keyword}, '%')
          OR EXISTS (
            SELECT 1
            FROM bills.b_transaction_entries ke
            JOIN bills.b_accounts ka ON ka.account_id = ke.account_id
            WHERE ke.transaction_id = t.transaction_id
              AND (ka.account_name ILIKE CONCAT('%', #{keyword}, '%')
                   OR ka.account_code ILIKE CONCAT('%', #{keyword}, '%'))
          )
          OR EXISTS (
            SELECT 1
            FROM bills.b_transaction_allocations kal
            JOIN bills.b_persons kp ON kp.person_id = kal.person_id
            WHERE kal.transaction_id = t.transaction_id
              AND kp.person_name ILIKE CONCAT('%', #{keyword}, '%')
          )
        )
      </if>
      ORDER BY
        <choose>
          <when test="sort == 'TIME_ASC'">t.transaction_time ASC, t.created_at ASC, t.transaction_id ASC</when>
          <otherwise>t.transaction_time DESC, t.created_at DESC, t.transaction_id DESC</otherwise>
        </choose>
      </script>
      """)
  List<TransactionLedgerCandidate> selectCandidates(
      @Param("startInclusive") Instant startInclusive,
      @Param("endExclusive") Instant endExclusive,
      @Param("keyword") String keyword,
      @Param("keywordRegex") boolean keywordRegex,
      @Param("accountId") String accountId,
      @Param("accountTypeCode") String accountTypeCode,
      @Param("categoryId") String categoryId,
      @Param("personIds") List<String> personIds,
      @Param("unassignedPerson") boolean unassignedPerson,
      @Param("transactionTypeCode") String transactionTypeCode,
      @Param("sort") String sort);
}
