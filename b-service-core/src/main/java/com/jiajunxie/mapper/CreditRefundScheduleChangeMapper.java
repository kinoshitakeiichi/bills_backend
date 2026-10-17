package com.jiajunxie.mapper;

import com.jiajunxie.model.CreditRefundScheduleChange;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CreditRefundScheduleChangeMapper {

  @Insert(
      """
      INSERT INTO bills.b_credit_refund_schedule_changes
      (change_id, refund_transaction_id, purchase_transaction_id, schedule_id, change_order,
       principal_reduction, schedule_deleted, installment_plan_id, liability_account_id,
       installment_no, installment_count, principal_before, fee_before, adjustment_before,
       currency, payment_type_code, scheduled_billing_date, scheduled_payment_date,
       calculated_payment_date, manual_payment_date, status_code, schedule_created_at, created_at)
      VALUES
      (#{changeId}, #{refundTransactionId}, #{purchaseTransactionId}, #{scheduleId}, #{changeOrder},
       #{principalReduction}, #{scheduleDeleted}, #{installmentPlanId}, #{liabilityAccountId},
       #{installmentNo}, #{installmentCount}, #{principalBefore}, #{feeBefore}, #{adjustmentBefore},
       #{currency}, #{paymentTypeCode}, #{scheduledBillingDate}, #{scheduledPaymentDate},
       #{calculatedPaymentDate}, #{manualPaymentDate}, #{statusCode}, #{scheduleCreatedAt}, #{createdAt})
      """)
  int insert(CreditRefundScheduleChange change);

  @Select(
      """
      SELECT change_id, refund_transaction_id, purchase_transaction_id, schedule_id, change_order,
             principal_reduction, schedule_deleted, installment_plan_id, liability_account_id,
             installment_no, installment_count, principal_before, fee_before, adjustment_before,
             currency, payment_type_code, scheduled_billing_date, scheduled_payment_date,
             calculated_payment_date, manual_payment_date, status_code, schedule_created_at, created_at
      FROM bills.b_credit_refund_schedule_changes
      WHERE refund_transaction_id = #{refundTransactionId}
      ORDER BY change_order
      """)
  List<CreditRefundScheduleChange> selectByRefundTransactionId(
      @Param("refundTransactionId") String refundTransactionId);

  @Delete(
      """
      DELETE FROM bills.b_credit_refund_schedule_changes
      WHERE refund_transaction_id = #{refundTransactionId}
      """)
  int deleteByRefundTransactionId(@Param("refundTransactionId") String refundTransactionId);
}
