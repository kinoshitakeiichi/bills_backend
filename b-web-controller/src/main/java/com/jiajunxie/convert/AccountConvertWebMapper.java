package com.jiajunxie.convert;

import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import com.jiajunxie.request.AccountRequest;
import com.jiajunxie.response.AccountIdLabelResponse;
import com.jiajunxie.response.AccountResponse;
import com.jiajunxie.response.AccountOptionResponse;
import com.jiajunxie.vo.AccountIdLabelVo;
import com.jiajunxie.vo.AccountInfoVo;
import com.jiajunxie.vo.AccountOptionVo;
import com.jiajunxie.vo.AccountVo;

/**
 * Web-layer account mapping between HTTP DTOs and service VO contracts.
 */
@Mapper(componentModel = "spring")
public interface AccountConvertWebMapper extends WebMapperSupport {

  @Mapping(target = "accountName", source = "accountName", qualifiedByName = "trim")
  @Mapping(target = "accountCode", source = "accountCode", qualifiedByName = "trim")
  @Mapping(target = "issuer", source = "issuer", qualifiedByName = "emptyToNull")
  @Mapping(target = "accountTypeCode", source = "accountTypeCode", qualifiedByName = "upperTrim")
  @Mapping(target = "currency", source = "currency", qualifiedByName = "upperTrim")
  @Mapping(target = "ownerPersonId", source = "ownerPersonId", qualifiedByName = "trim")
  @Mapping(target = "parentAccountId", source = "parentAccountId", qualifiedByName = "emptyToNull")
  @Mapping(target = "autoDebitAccountId", source = "autoDebitAccountId", qualifiedByName = "emptyToNull")
  AccountVo toVo(AccountRequest accountRequest);

  List<AccountResponse> toResponses(List<AccountInfoVo> accountInfoVos);

  List<AccountIdLabelResponse> toAccountIdLabelResponses(List<AccountIdLabelVo> accountIdLabelVos);

  List<AccountOptionResponse> toOptionResponses(List<AccountOptionVo> options);
}
