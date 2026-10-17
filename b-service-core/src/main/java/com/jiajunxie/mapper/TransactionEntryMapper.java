package com.jiajunxie.mapper;

import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jiajunxie.entity.TransactionEntry;

@Mapper
public interface TransactionEntryMapper extends BaseMapper<TransactionEntry> {

  @Select(
      """
      SELECT entry_id,
             transaction_id,
             account_id,
             amount,
             currency,
             entry_role_code AS entry_role,
             created_at
      FROM bills.b_transaction_entries
      WHERE transaction_id = #{transactionId}
      ORDER BY created_at, entry_id
      """)
  List<TransactionEntry> selectByTransactionId(@Param("transactionId") String transactionId);

  @Delete(
      """
      DELETE FROM bills.b_transaction_entries
      WHERE transaction_id = #{transactionId}
      """)
  int deleteByTransactionId(@Param("transactionId") String transactionId);

  @Select(
      """
      SELECT e.transaction_id
      FROM bills.b_transaction_entries e
      JOIN bills.b_transactions t ON t.transaction_id = e.transaction_id
      WHERE e.account_id = #{accountId}
      GROUP BY e.transaction_id
      ORDER BY MIN(t.transaction_time), MIN(t.created_at), e.transaction_id
      """)
  List<String> selectTransactionIdsByAccountIdChronological(@Param("accountId") String accountId);

  @Select(
      """
      SELECT COALESCE(SUM(amount), 0)
      FROM bills.b_transaction_entries
      WHERE account_id = #{accountId}
      """)
  BigDecimal sumAmountByAccountId(@Param("accountId") String accountId);
}
