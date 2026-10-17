package com.jiajunxie.mapper;

import java.math.BigDecimal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jiajunxie.entity.Account;

@Mapper
public interface AccountMapper extends BaseMapper<Account> {

  @Select(
      """
      SELECT account_id
      FROM bills.b_accounts
      WHERE account_id = #{accountId}
      FOR UPDATE
      """)
  String lockById(@Param("accountId") String accountId);

  @Update(
      """
      UPDATE bills.b_accounts
      SET total_amount = COALESCE(total_amount, 0) + #{delta}
      WHERE account_id = #{accountId}
      """)
  int addBalance(
      @Param("accountId") String accountId,
      @Param("delta") BigDecimal delta);

  @Update(
      """
      UPDATE bills.b_accounts
      SET total_amount = #{balance}
      WHERE account_id = #{accountId}
      """)
  int replaceBalance(
      @Param("accountId") String accountId,
      @Param("balance") BigDecimal balance);

  @Update(
      """
      UPDATE bills.b_accounts
      SET parent_account_id = #{parentAccountId}
      WHERE account_id = #{mainAccount}
      AND currency = #{currency}
      """)
  Integer updateParentAccount(
      @Param("mainAccount") String mainAccount,
      @Param("parentAccountId") String parentAccountId,
      @Param("currency") String currency);
}
