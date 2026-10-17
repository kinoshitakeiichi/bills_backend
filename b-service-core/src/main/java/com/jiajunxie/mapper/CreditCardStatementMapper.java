package com.jiajunxie.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jiajunxie.entity.CreditCardStatement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CreditCardStatementMapper extends BaseMapper<CreditCardStatement> {

  @Select(
      """
      SELECT *
      FROM bills.b_credit_card_statements
      WHERE statement_id = #{statementId}
      FOR UPDATE
      """)
  CreditCardStatement selectByIdForUpdate(@Param("statementId") String statementId);
}
