package com.jiajunxie.convert;

import java.util.List;
import java.util.Locale;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import com.jiajunxie.entity.Transaction;
import com.jiajunxie.entity.TransactionEntry;
import com.jiajunxie.entity.TransactionAllocation;
import com.jiajunxie.model.AccountTransactionRow;
import com.jiajunxie.vo.AccountTransactionVo;
import com.jiajunxie.vo.AllocationVo;
import com.jiajunxie.vo.TransactionCreateVo;
import com.jiajunxie.vo.TransactionDetailVo;
import com.jiajunxie.vo.InstallmentPlanVo;
import com.jiajunxie.vo.TransactionEntryVo;

/**
 * Service-layer transaction mapping only; business rules must stay in TransactionService.
 */
@Mapper(componentModel = "spring")
public interface TransactionConvertServiceMapper extends MapperSupport {

  @Mapping(target = "transactionId", ignore = true)
  @Mapping(target = "transactionType", source = "transactionType", qualifiedByName = "normalize")
  @Mapping(target = "categoryId", source = "categoryId", qualifiedByName = "trimToNull")
  @Mapping(target = "merchant", source = "merchant", qualifiedByName = "trimToNull")
  @Mapping(target = "remark", source = "remark", qualifiedByName = "trimToNull")
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "category", ignore = true)
  @Mapping(target = "originCode", ignore = true)
  @Mapping(target = "originReferenceId", ignore = true)
  Transaction toEntity(TransactionCreateVo vo);

  @Mapping(target = "entryId", ignore = true)
  @Mapping(target = "transactionId", ignore = true)
  @Mapping(target = "currency", source = "currency", qualifiedByName = "normalize")
  @Mapping(target = "entryRole", source = "entryRole", qualifiedByName = "normalize")
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "transaction", ignore = true)
  @Mapping(target = "account", ignore = true)
  TransactionEntry toEntryEntity(TransactionEntryVo vo);

  TransactionEntryVo toEntryVo(TransactionEntry entry);

  List<TransactionEntryVo> toEntryVos(List<TransactionEntry> entries);

  @Mapping(target = "transactionId", source = "transaction.transactionId")
  @Mapping(target = "transactionType", source = "transaction.transactionType")
  @Mapping(target = "categoryId", source = "transaction.categoryId")
  @Mapping(target = "merchant", source = "transaction.merchant")
  @Mapping(target = "remark", source = "transaction.remark")
  @Mapping(target = "transactionTime", source = "transaction.transactionTime")
  @Mapping(target = "entries", source = "entries")
  @Mapping(target = "allocations", source = "allocations")
  @Mapping(target = "paymentPlan", source = "paymentPlan")
  TransactionDetailVo toDetailVo(
      Transaction transaction,
      List<TransactionEntryVo> entries,
      List<AllocationVo> allocations,
      InstallmentPlanVo paymentPlan);

  AllocationVo toAllocationVo(TransactionAllocation allocation);

  List<AllocationVo> toAllocationVos(List<TransactionAllocation> allocations);

  @Mapping(target = "loanPrincipalAmount", source = "loanPrincipalAmount")
  @Mapping(target = "loanInterestAmount", source = "loanInterestAmount")
  @Mapping(target = "loanFeeAmount", source = "loanFeeAmount")
  @Mapping(target = "loanTotalPaymentAmount", source = "loanTotalPaymentAmount")
  @Mapping(target = "loanSourceAccountId", source = "loanSourceAccountId")
  @Mapping(target = "loanSourceAccountName", source = "loanSourceAccountName")
  AccountTransactionVo toAccountTransactionVo(AccountTransactionRow row);

  List<AccountTransactionVo> toAccountTransactionVos(List<AccountTransactionRow> rows);

  @Named("normalize")
  default String normalize(String value) {
    return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
  }

  @Named("trimToNull")
  default String trimToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
