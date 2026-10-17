package com.jiajunxie.convert;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import com.jiajunxie.request.TransactionRequest;
import com.jiajunxie.request.TransactionPaymentRequest;
import com.jiajunxie.request.StatementPaymentAllocationRequest;
import com.jiajunxie.response.AccountTransactionMonthResponse;
import com.jiajunxie.response.AccountTransactionResponse;
import com.jiajunxie.response.TransactionDetailResponse;
import com.jiajunxie.response.InstallmentPlanResponse;
import com.jiajunxie.response.PaymentScheduleResponse;
import com.jiajunxie.vo.AccountTransactionMonthVo;
import com.jiajunxie.vo.AccountTransactionVo;
import com.jiajunxie.vo.TransactionCreateVo;
import com.jiajunxie.vo.TransactionPaymentVo;
import com.jiajunxie.vo.TransactionDetailVo;
import com.jiajunxie.vo.InstallmentPlanVo;
import com.jiajunxie.vo.PaymentScheduleVo;
import com.jiajunxie.vo.StatementPaymentAllocationCommandVo;

/**
 * Web-layer transaction mapping between HTTP DTOs and service VO contracts.
 */
@Mapper(componentModel = "spring")
public interface TransactionConvertWebMapper extends WebMapperSupport {

  @Mapping(target = "transactionId", ignore = true)
  @Mapping(target = "categoryId", source = "categoryId", qualifiedByName = "emptyToNull")
  @Mapping(target = "merchant", source = "merchant", qualifiedByName = "emptyToNull")
  @Mapping(target = "remark", source = "remark", qualifiedByName = "emptyToNull")
  TransactionCreateVo toCreateVo(TransactionRequest request);

  TransactionPaymentVo toPaymentVo(TransactionPaymentRequest request);

  StatementPaymentAllocationCommandVo toStatementPaymentAllocationVo(
      StatementPaymentAllocationRequest request);

  InstallmentPlanResponse toPaymentPlanResponse(InstallmentPlanVo vo);

  PaymentScheduleResponse toPaymentScheduleResponse(PaymentScheduleVo vo);

  @Mapping(target = "originCode", source = "originCode")
  @Mapping(target = "originReferenceId", source = "originReferenceId")
  @Mapping(target = "loanPrincipalAmount", source = "loanPrincipalAmount")
  @Mapping(target = "loanInterestAmount", source = "loanInterestAmount")
  @Mapping(target = "loanFeeAmount", source = "loanFeeAmount")
  @Mapping(target = "loanTotalPaymentAmount", source = "loanTotalPaymentAmount")
  @Mapping(target = "loanSourceAccountId", source = "loanSourceAccountId")
  @Mapping(target = "loanSourceAccountName", source = "loanSourceAccountName")
  AccountTransactionResponse toAccountTransactionResponse(AccountTransactionVo vo);

  AccountTransactionMonthResponse toMonthResponse(AccountTransactionMonthVo vo);

  TransactionDetailResponse toDetailResponse(TransactionDetailVo vo);
}
