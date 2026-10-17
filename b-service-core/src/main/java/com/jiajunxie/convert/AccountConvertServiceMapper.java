package com.jiajunxie.convert;

import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import com.jiajunxie.entity.Account;
import com.jiajunxie.vo.AccountIdLabelVo;
import com.jiajunxie.vo.AccountInfoVo;
import com.jiajunxie.vo.AccountOptionVo;
import com.jiajunxie.vo.AccountVo;

/**
 * Service-layer account mapping only; account validation and class derivation stay in
 * AccountService.
 */
@Mapper(componentModel = "spring")
public interface AccountConvertServiceMapper extends MapperSupport {

  @Mapping(target = "ownerPersonId", source = "ownerPersonId")
  AccountVo toVo(Account account);

  /**
   * totalAmount 不允许通过主数据表单更新。 ownerPersonId 对应 AccountVo.ownerPersonId。 isBaseAccount 当前没有出现在
   * AccountVo 中，保留实体默认值。
   */
  @Mapping(target = "ownerPersonId", source = "ownerPersonId")
  @Mapping(target = "issuer", source = "issuer", qualifiedByName = "emptyToNull")
  @Mapping(target = "parentAccountId", source = "parentAccountId", qualifiedByName = "emptyToNull")
  @Mapping(
      target = "autoDebitAccountId",
      source = "autoDebitAccountId",
      qualifiedByName = "emptyToNull")
  @Mapping(target = "totalAmount", ignore = true)
  @Mapping(target = "isBaseAccount", ignore = true)
  @Mapping(target = "enabled", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "ownerPerson", ignore = true)
  @Mapping(target = "parentAccountEntity", ignore = true)
  @Mapping(target = "autoDebitAccountEntity", ignore = true)
  Account toEntity(AccountVo accountVo);

  /**
   * Maps an account card read model. Person name is supplied explicitly because MyBatis account
   * queries intentionally persist/reference only ownerPersonId and do not hydrate JPA relations.
   */
  @Mapping(target = "ownerPersonId", source = "account.ownerPersonId")
  @Mapping(target = "ownerPersonName", source = "ownerPersonName")
  @Mapping(target = "accountClassName", source = "accountClassName")
  @Mapping(target = "accountTypeName", source = "accountTypeName")
  AccountInfoVo toInfoVo(
      Account account, String ownerPersonName, String accountClassName, String accountTypeName);

  @Mapping(target = "value", source = "accountId")
  @Mapping(target = "label", expression = "java(buildLabel(account))")
  AccountIdLabelVo toIdLabel(Account account);

  List<AccountIdLabelVo> toIdLabelList(List<Account> accounts);

  default AccountOptionVo toOption(Account account) {
    return new AccountOptionVo(
        account.getAccountId(),
        buildLabel(account),
        account.getAccountClassCode(),
        account.getAccountTypeCode(),
        account.getCurrency(),
        account.getOwnerPersonId(),
        account.getIsBaseAccount());
  }

  default List<AccountOptionVo> toOptions(List<Account> accounts) {
    return accounts.stream().map(this::toOption).toList();
  }

  default String buildLabel(Account account) {
    return "%s的%s (%s)"
        .formatted(
            account.getOwnerPerson().getPersonName(),
            account.getAccountName(),
            account.getCurrency());
  }
}
