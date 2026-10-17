package com.jiajunxie.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jiajunxie.entity.LoanPaymentDetail;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface LoanPaymentDetailMapper extends BaseMapper<LoanPaymentDetail> {

  @Select(
      """
      <script>
      SELECT
          transaction_id AS "transactionId",
          loan_account_id AS "loanAccountId",
          principal_amount AS "principalAmount",
          interest_amount AS "interestAmount",
          fee_amount AS "feeAmount",
          currency,
          created_at AS "createdAt"
      FROM bills.b_loan_payment_details
      WHERE transaction_id IN
      <foreach collection="transactionIds" item="transactionId" open="(" separator="," close=")">
        #{transactionId}
      </foreach>
      </script>
      """)
  List<LoanPaymentDetail> selectByTransactionIds(
      @Param("transactionIds") List<String> transactionIds);
}
