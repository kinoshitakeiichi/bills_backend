package com.jiajunxie.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.common.exception.CommonErrorCode;
import com.jiajunxie.entity.Account;
import com.jiajunxie.entity.Category;
import com.jiajunxie.entity.LoanProfile;
import com.jiajunxie.entity.InstallmentPlan;
import com.jiajunxie.entity.Transaction;
import com.jiajunxie.entity.TransactionEntry;
import com.jiajunxie.entity.TransactionAllocation;
import com.jiajunxie.entity.Person;
import com.jiajunxie.mapper.AccountMapper;
import com.jiajunxie.mapper.CategoryMapper;
import com.jiajunxie.mapper.LoanProfileMapper;
import com.jiajunxie.mapper.InstallmentPlanMapper;
import com.jiajunxie.mapper.TransactionEntryMapper;
import com.jiajunxie.mapper.TransactionMapper;
import com.jiajunxie.mapper.TransactionAllocationMapper;
import com.jiajunxie.mapper.PersonMapper;
import com.jiajunxie.service.LoanPurchaseService;
import com.jiajunxie.vo.LoanPurchaseCreateVo;
import com.jiajunxie.vo.LoanPurchaseCreatedVo;
import com.jiajunxie.vo.LoanPurchaseAdoptedVo;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LoanPurchaseServiceImpl implements LoanPurchaseService {
  private static final String TYPE = "LOAN_PURCHASE";
  private static final String ORIGIN = "LOAN_ENGINE";
  private static final Set<String> SUPPORTED_MODES = Set.of("DIRECT_FINANCING", "PURCHASE_FINANCING");

  private final TransactionMapper transactionMapper;
  private final TransactionEntryMapper entryMapper;
  private final AccountMapper accountMapper;
  private final LoanProfileMapper profileMapper;
  private final CategoryMapper categoryMapper;
  private final InstallmentPlanMapper installmentPlanMapper;
  private final TransactionAllocationMapper allocationMapper;
  private final PersonMapper personMapper;
  private final LoanLifecycleDeletionGuard loanLifecycleDeletionGuard;

  @Override
  @Transactional
  public LoanPurchaseCreatedVo create(LoanPurchaseCreateVo command) {
    validateShape(command);
    Account loan = accountMapper.selectById(command.loanAccountId());
    if (loan == null) throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "贷款账户不存在");
    LoanProfile profile = profileMapper.selectById(command.loanAccountId());
    if (profile == null) throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "贷款账户尚未配置 Loan Profile");
    String mode = normalize(profile.getFundingModeCode());
    if (!SUPPORTED_MODES.contains(mode)) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "当前贷款不是直接融资/逐笔消费融资模式");
    }
    if (!Boolean.TRUE.equals(loan.getEnabled()) || !"LIABILITY".equals(normalize(loan.getAccountClassCode()))) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "融资消费只能使用启用的贷款负债账户");
    }
    String currency = normalize(command.currency());
    if (!currency.equals(normalize(loan.getCurrency())) || !currency.equals(normalize(profile.getCurrency()))) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "融资消费币种必须与贷款账户币种一致");
    }
    Category category = categoryMapper.selectById(command.categoryId());
    if (category == null || !Boolean.TRUE.equals(category.getEnabled()) || !"EXPENSE".equals(normalize(category.getCategoryTypeCode()))) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "融资消费必须选择启用的支出分类");
    }
    Long childCount = categoryMapper.selectCount(
        new LambdaQueryWrapper<Category>()
            .eq(Category::getParentId, command.categoryId())
            .eq(Category::getEnabled, true));
    if (childCount != null && childCount > 0) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "融资消费只能选择叶子支出分类");
    }
    if ("DIRECT_FINANCING".equals(mode)) {
      if (profile.getOriginalPrincipal() == null || profile.getOriginalPrincipal().compareTo(command.amount()) != 0) {
        throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "直接融资金额必须等于 Loan Profile 初始本金");
      }
      if (hasPurchase(command.loanAccountId())) {
        throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "该直接融资贷款已经登记过融资消费");
      }
    }
    validateAllocations(command.allocations(), command.amount(), currency);

    String transactionId = UUID.randomUUID().toString();
    Instant now = Instant.now();
    Transaction tx = new Transaction();
    tx.setTransactionId(transactionId);
    tx.setTransactionType(TYPE);
    tx.setCategoryId(command.categoryId());
    tx.setMerchant(trimToNull(command.merchant()));
    tx.setRemark(trimToNull(command.remark()));
    tx.setTransactionTime(command.transactionTime() == null ? now : command.transactionTime());
    tx.setOriginCode(ORIGIN);
    tx.setOriginReferenceId(loan.getAccountId());
    tx.setCreatedAt(now);
    tx.setUpdatedAt(now);
    transactionMapper.insert(tx);

    TransactionEntry entry = new TransactionEntry();
    entry.setEntryId(UUID.randomUUID().toString());
    entry.setTransactionId(transactionId);
    entry.setAccountId(loan.getAccountId());
    entry.setAmount(command.amount());
    entry.setCurrency(currency);
    entry.setEntryRole("SINGLE");
    entry.setCreatedAt(now);
    entryMapper.insert(entry);
    if (accountMapper.addBalance(loan.getAccountId(), command.amount()) != 1) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "贷款余额更新失败");
    }
    applyAllocations(transactionId, command.allocations(), now);
    return new LoanPurchaseCreatedVo(transactionId, loan.getAccountId(), command.amount(), currency);
  }

  @Override
  @Transactional
  public LoanPurchaseCreatedVo update(String transactionId, LoanPurchaseCreateVo command) {
    requireText(transactionId, "融资消费交易不能为空");
    validateShape(command);

    Transaction tx = transactionMapper.selectById(transactionId);
    if (tx == null || !TYPE.equals(normalize(tx.getTransactionType()))) {
      throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "融资消费交易不存在");
    }

    var entries = entryMapper.selectList(
        new LambdaQueryWrapper<TransactionEntry>()
            .eq(TransactionEntry::getTransactionId, transactionId));
    if (entries.size() != 1) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "融资消费必须恰好只有一条贷款分录");
    }
    TransactionEntry entry = entries.get(0);
    if (!"SINGLE".equals(normalize(entry.getEntryRole())) || entry.getAmount() == null) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "融资消费贷款分录结构不正确");
    }
    if (!entry.getAccountId().equals(command.loanAccountId())) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "融资消费不能改到其他贷款账户");
    }

    Account loan = accountMapper.selectById(command.loanAccountId());
    if (loan == null) throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "贷款账户不存在");
    LoanProfile profile = profileMapper.selectById(command.loanAccountId());
    if (profile == null) throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "贷款账户尚未配置 Loan Profile");
    String mode = normalize(profile.getFundingModeCode());
    if (!SUPPORTED_MODES.contains(mode)) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "当前贷款不是直接融资/逐笔消费融资模式");
    }
    if (!Boolean.TRUE.equals(loan.getEnabled()) || !"LIABILITY".equals(normalize(loan.getAccountClassCode()))) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "融资消费只能使用启用的贷款负债账户");
    }
    String currency = normalize(command.currency());
    if (!currency.equals(normalize(loan.getCurrency())) || !currency.equals(normalize(profile.getCurrency()))
        || !currency.equals(normalize(entry.getCurrency()))) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "融资消费币种必须与贷款账户币种一致");
    }

    Category category = categoryMapper.selectById(command.categoryId());
    if (category == null || !Boolean.TRUE.equals(category.getEnabled())
        || !"EXPENSE".equals(normalize(category.getCategoryTypeCode()))) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "融资消费必须选择启用的支出分类");
    }
    Long childCount = categoryMapper.selectCount(
        new LambdaQueryWrapper<Category>()
            .eq(Category::getParentId, command.categoryId())
            .eq(Category::getEnabled, true));
    if (childCount != null && childCount > 0) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "融资消费只能选择叶子支出分类");
    }
    if ("DIRECT_FINANCING".equals(mode)
        && (profile.getOriginalPrincipal() == null || profile.getOriginalPrincipal().compareTo(command.amount()) != 0)) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "直接融资金额必须等于 Loan Profile 初始本金");
    }

    validateAllocations(command.allocations(), command.amount(), currency);

    if (entry.getAmount().compareTo(command.amount()) != 0) {
      Long planCount = installmentPlanMapper.selectCount(
          new LambdaQueryWrapper<InstallmentPlan>()
              .eq(InstallmentPlan::getSourceTransactionId, transactionId));
      if (planCount != null && planCount > 0) {
        throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "该融资消费已生成贷款计划，不能直接修改金额；请先调整/重建贷款计划");
      }
      BigDecimal delta = command.amount().subtract(entry.getAmount());
      if (accountMapper.addBalance(loan.getAccountId(), delta) != 1) {
        throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "贷款余额调整失败");
      }
      entry.setAmount(command.amount());
      if (entryMapper.updateById(entry) != 1) {
        throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "融资消费贷款分录更新失败");
      }
    }

    tx.setCategoryId(command.categoryId());
    tx.setMerchant(trimToNull(command.merchant()));
    tx.setRemark(trimToNull(command.remark()));
    if (command.transactionTime() != null) tx.setTransactionTime(command.transactionTime());
    tx.setUpdatedAt(Instant.now());
    if (transactionMapper.updateById(tx) != 1) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "融资消费交易更新失败");
    }

    allocationMapper.deleteByTransactionId(transactionId);
    applyAllocations(transactionId, command.allocations(), Instant.now());
    return new LoanPurchaseCreatedVo(transactionId, loan.getAccountId(), command.amount(), currency);
  }

  @Override
  @Transactional
  public LoanPurchaseAdoptedVo adoptLegacyPurchase(String transactionId, String loanAccountId) {
    requireText(transactionId, "历史融资交易不能为空");
    requireText(loanAccountId, "贷款账户不能为空");

    Account loan = accountMapper.selectById(loanAccountId);
    if (loan == null) throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "贷款账户不存在");
    if (!Boolean.TRUE.equals(loan.getEnabled())
        || !"LIABILITY".equals(normalize(loan.getAccountClassCode()))
        || !"CONSUMER_LOAN".equals(normalize(loan.getAccountTypeCode()))) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "接管目标必须是启用的消费贷款负债账户");
    }

    LoanProfile profile = profileMapper.selectById(loanAccountId);
    if (profile == null) throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "贷款账户尚未配置 Loan Profile");
    if (!"DIRECT_FINANCING".equals(normalize(profile.getFundingModeCode()))) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "仅 DIRECT_FINANCING 历史融资事实允许接管");
    }
    if (profile.getOriginalPrincipal() == null || profile.getOriginalPrincipal().signum() <= 0) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "Loan Profile 初始本金无效");
    }

    Transaction tx = transactionMapper.selectById(transactionId);
    if (tx == null) throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "历史融资交易不存在");
    if (!TYPE.equals(normalize(tx.getTransactionType()))) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "历史交易不是 LOAN_PURCHASE");
    }

    boolean alreadyAdopted =
        ORIGIN.equals(normalize(tx.getOriginCode()))
            && loanAccountId.equals(tx.getOriginReferenceId());

    if (!alreadyAdopted) {
      if (!"LEGACY_MIGRATION".equals(normalize(tx.getOriginCode()))) {
        throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "只允许接管 LEGACY_MIGRATION 融资事实");
      }
      if (!transactionId.equals(tx.getOriginReferenceId())) {
        throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "历史融资事实 originReferenceId 不符合 Stage3 结构");
      }
    }

    Long planCount = installmentPlanMapper.selectCount(
        new LambdaQueryWrapper<InstallmentPlan>()
            .eq(InstallmentPlan::getSourceTransactionId, transactionId));
    if (planCount != null && planCount > 0) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "该融资事实已经存在贷款计划，禁止再次接管");
    }

    var entries = entryMapper.selectList(
        new LambdaQueryWrapper<TransactionEntry>()
            .eq(TransactionEntry::getTransactionId, transactionId));
    if (entries.size() != 1) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "历史融资事实必须恰好只有一条 Entry");
    }

    TransactionEntry entry = entries.get(0);
    if (!loanAccountId.equals(entry.getAccountId())
        || !"SINGLE".equals(normalize(entry.getEntryRole()))
        || entry.getAmount() == null
        || entry.getAmount().signum() <= 0) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "历史融资 Entry 与目标贷款账户不匹配");
    }
    if (entry.getAmount().compareTo(profile.getOriginalPrincipal()) != 0) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "历史融资本金与 Loan Profile 初始本金不一致");
    }
    if (!normalize(loan.getCurrency()).equals(normalize(entry.getCurrency()))
        || !normalize(profile.getCurrency()).equals(normalize(entry.getCurrency()))) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "历史融资币种与贷款账户/Profile 不一致");
    }

    if (!alreadyAdopted) {
      tx.setOriginCode(ORIGIN);
      tx.setOriginReferenceId(loanAccountId);
      tx.setUpdatedAt(Instant.now());
      if (transactionMapper.updateById(tx) != 1) {
        throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "历史融资事实接管失败");
      }
    }

    return new LoanPurchaseAdoptedVo(
        transactionId, loanAccountId, entry.getAmount(), entry.getCurrency(), !alreadyAdopted);
  }

  @Override
  public boolean hasPurchase(String loanAccountId) {
    if (loanAccountId == null || loanAccountId.isBlank()) return false;
    Long count = transactionMapper.selectCount(new LambdaQueryWrapper<Transaction>()
        .eq(Transaction::getTransactionType, TYPE)
        .eq(Transaction::getOriginReferenceId, loanAccountId));
    return count != null && count > 0;
  }

@Override
@Transactional
public int remove(String transactionId) {
  requireText(transactionId, "融资消费交易不能为空");

  Transaction tx = transactionMapper.selectById(transactionId);
  if (tx == null || !TYPE.equals(normalize(tx.getTransactionType()))) {
    throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "融资消费交易不存在");
  }
  if (!ORIGIN.equals(normalize(tx.getOriginCode()))) {
    throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "仅 Loan Engine 生成的融资消费允许撤销");
  }

  String loanAccountId = tx.getOriginReferenceId();
  requireText(loanAccountId, "融资消费缺少贷款账户引用");
  loanLifecycleDeletionGuard.assertLoanAccountDeletable(loanAccountId);

  Long planCount =
      installmentPlanMapper.selectCount(
          new LambdaQueryWrapper<InstallmentPlan>()
              .eq(InstallmentPlan::getSourceTransactionId, transactionId));
  if (planCount != null && planCount > 0) {
    throw new BusinessException(
        CommonErrorCode.DATA_CONFLICT, "该融资消费已经生成还款计划，请先删除未还款计划");
  }

  var entries =
      entryMapper.selectList(
          new LambdaQueryWrapper<TransactionEntry>()
              .eq(TransactionEntry::getTransactionId, transactionId));
  if (entries.size() != 1) {
    throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "融资消费分录结构异常，不能安全撤销");
  }
  TransactionEntry entry = entries.get(0);
  if (!loanAccountId.equals(entry.getAccountId())
      || !"SINGLE".equals(normalize(entry.getEntryRole()))
      || entry.getAmount() == null
      || entry.getAmount().signum() <= 0) {
    throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "融资消费贷款分录不一致，不能安全撤销");
  }

  if (accountMapper.addBalance(loanAccountId, entry.getAmount().negate()) != 1) {
    throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "融资消费撤销贷款余额恢复失败");
  }
  allocationMapper.deleteByTransactionId(transactionId);
  entryMapper.deleteByTransactionId(transactionId);
  return transactionMapper.deleteById(transactionId);
}

  private void validateAllocations(List<com.jiajunxie.vo.AllocationVo> allocations, BigDecimal expectedAmount, String expectedCurrency) {
    if (allocations == null || allocations.isEmpty()) return;
    Set<String> personIds = new HashSet<>();
    BigDecimal total = BigDecimal.ZERO;
    BigDecimal ratioTotal = BigDecimal.ZERO;
    boolean hasRatio = false;
    boolean missingRatio = false;
    for (var allocation : allocations) {
      requireText(allocation.personId(), "分摊人员不能为空");
      if (!personIds.add(allocation.personId())) {
        throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "同一人员不能重复分摊");
      }
      Person person = personMapper.selectById(allocation.personId());
      if (person == null || !Boolean.TRUE.equals(person.getEnabled())) {
        throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "分摊人员不存在或已停用");
      }
      if (allocation.allocatedAmount() == null || allocation.allocatedAmount().signum() <= 0) {
        throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "分摊金额必须大于零");
      }
      if (!expectedCurrency.equals(normalize(allocation.currency()))) {
        throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "分摊币种必须与融资消费币种一致");
      }
      total = total.add(allocation.allocatedAmount());
      if (allocation.allocationRatio() == null) {
        missingRatio = true;
      } else {
        hasRatio = true;
        if (allocation.allocationRatio().signum() <= 0 || allocation.allocationRatio().compareTo(BigDecimal.ONE) > 0) {
          throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "分摊比例必须大于 0 且不超过 1");
        }
        ratioTotal = ratioTotal.add(allocation.allocationRatio());
      }
    }
    if (total.compareTo(expectedAmount) != 0) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "人员分摊金额合计必须等于融资消费金额");
    }
    if (hasRatio && missingRatio) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "分摊比例必须全部填写或全部留空");
    }
    if (hasRatio && ratioTotal.compareTo(BigDecimal.ONE) != 0) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "人员分摊比例合计必须等于 1");
    }
  }

  private void applyAllocations(String transactionId, List<com.jiajunxie.vo.AllocationVo> allocations, Instant now) {
    if (allocations == null || allocations.isEmpty()) return;
    for (var allocation : allocations) {
      TransactionAllocation entity = new TransactionAllocation();
      entity.setAllocationId(UUID.randomUUID().toString());
      entity.setTransactionId(transactionId);
      entity.setPersonId(allocation.personId());
      entity.setAllocatedAmount(allocation.allocatedAmount());
      entity.setCurrency(normalize(allocation.currency()));
      entity.setAllocationRatio(allocation.allocationRatio());
      entity.setCreatedAt(now);
      allocationMapper.insert(entity);
    }
  }

  private void validateShape(LoanPurchaseCreateVo command) {
    if (command == null) throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "融资消费请求不能为空");
    requireText(command.loanAccountId(), "贷款账户不能为空");
    requireText(command.categoryId(), "支出分类不能为空");
    requireText(command.currency(), "币种不能为空");
    if (command.amount() == null || command.amount().signum() <= 0) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "融资消费金额必须大于 0");
    }
  }
  private void requireText(String value, String message) {
    if (value == null || value.isBlank()) throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, message);
  }
  private String normalize(String value) { return value == null ? null : value.trim().toUpperCase(Locale.ROOT); }
  private String trimToNull(String value) {
    if (value == null) return null; String v=value.trim(); return v.isEmpty()?null:v;
  }
}
