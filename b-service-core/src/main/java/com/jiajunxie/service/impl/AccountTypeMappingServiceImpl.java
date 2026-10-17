package com.jiajunxie.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.common.exception.CommonErrorCode;
import com.jiajunxie.entity.Account;
import com.jiajunxie.entity.AccountTypeClassMapping;
import com.jiajunxie.entity.DictionaryItem;
import com.jiajunxie.entity.DictionaryType;
import com.jiajunxie.mapper.AccountMapper;
import com.jiajunxie.mapper.AccountTypeClassMappingMapper;
import com.jiajunxie.mapper.DictionaryItemMapper;
import com.jiajunxie.mapper.DictionaryTypeMapper;
import com.jiajunxie.service.AccountTypeMappingService;
import com.jiajunxie.vo.AccountTypeMappingVo;
import com.jiajunxie.vo.AccountTypeOptionVo;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AccountTypeMappingServiceImpl implements AccountTypeMappingService {

  private static final String ACCOUNT_TYPE = "ACCOUNT_TYPE";
  private static final String ACCOUNT_CLASS = "ACCOUNT_CLASS";

  private final DictionaryTypeMapper dictionaryTypeMapper;
  private final DictionaryItemMapper dictionaryItemMapper;
  private final AccountTypeClassMappingMapper mappingMapper;
  private final AccountMapper accountMapper;

  @Override
  public List<AccountTypeMappingVo> listMappings() {
    DictionaryType type = requireDictionaryType(ACCOUNT_TYPE);
    DictionaryType accountClass = requireDictionaryType(ACCOUNT_CLASS);

    List<DictionaryItem> typeItems = dictionaryItemMapper.selectList(
        Wrappers.<DictionaryItem>lambdaQuery()
            .eq(DictionaryItem::getDictionaryTypeId, type.getDictionaryTypeId())
            .orderByAsc(DictionaryItem::getSortOrder)
            .orderByAsc(DictionaryItem::getItemCode));
    if (typeItems.isEmpty()) return List.of();

    List<String> typeItemIds = typeItems.stream().map(DictionaryItem::getDictionaryItemId).toList();
    Map<String, AccountTypeClassMapping> mappingsByTypeId = mappingMapper.selectByIds(typeItemIds).stream()
        .collect(Collectors.toMap(AccountTypeClassMapping::getAccountTypeItemId, Function.identity()));

    List<String> classItemIds = mappingsByTypeId.values().stream()
        .map(AccountTypeClassMapping::getAccountClassItemId)
        .distinct()
        .toList();
    Map<String, DictionaryItem> classesById = classItemIds.isEmpty()
        ? Map.of()
        : dictionaryItemMapper.selectByIds(classItemIds).stream()
            .filter(item -> accountClass.getDictionaryTypeId().equals(item.getDictionaryTypeId()))
            .collect(Collectors.toMap(DictionaryItem::getDictionaryItemId, Function.identity()));

    return typeItems.stream().map(typeItem -> {
      AccountTypeClassMapping mapping = mappingsByTypeId.get(typeItem.getDictionaryItemId());
      DictionaryItem classItem = mapping == null ? null : classesById.get(mapping.getAccountClassItemId());
      return new AccountTypeMappingVo(
          typeItem.getDictionaryItemId(),
          typeItem.getItemCode(),
          typeItem.getItemName(),
          classItem == null ? null : classItem.getDictionaryItemId(),
          classItem == null ? null : classItem.getItemCode(),
          classItem == null ? null : classItem.getItemName());
    }).toList();
  }

  @Override
  public List<AccountTypeOptionVo> listOptions(String keyword) {
    String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim().toLowerCase(Locale.ROOT) : null;
    DictionaryType type = requireDictionaryType(ACCOUNT_TYPE);
    DictionaryType accountClass = requireDictionaryType(ACCOUNT_CLASS);
    Map<String, DictionaryItem> enabledTypesById = dictionaryItemMapper.selectList(
        Wrappers.<DictionaryItem>lambdaQuery()
            .eq(DictionaryItem::getDictionaryTypeId, type.getDictionaryTypeId())
            .eq(DictionaryItem::getEnabled, true))
        .stream().collect(Collectors.toMap(DictionaryItem::getDictionaryItemId, Function.identity()));
    if (enabledTypesById.isEmpty()) return List.of();

    List<AccountTypeClassMapping> mappings = mappingMapper.selectByIds(enabledTypesById.keySet());
    List<String> classIds = mappings.stream().map(AccountTypeClassMapping::getAccountClassItemId).distinct().toList();
    Map<String, DictionaryItem> enabledClassesById = classIds.isEmpty()
        ? Map.of()
        : dictionaryItemMapper.selectByIds(classIds).stream()
            .filter(item -> accountClass.getDictionaryTypeId().equals(item.getDictionaryTypeId()))
            .filter(item -> Boolean.TRUE.equals(item.getEnabled()))
            .collect(Collectors.toMap(DictionaryItem::getDictionaryItemId, Function.identity()));

    return mappings.stream()
        .filter(mapping -> enabledTypesById.containsKey(mapping.getAccountTypeItemId())
            && enabledClassesById.containsKey(mapping.getAccountClassItemId()))
        .map(mapping -> Map.entry(
            enabledTypesById.get(mapping.getAccountTypeItemId()),
            enabledClassesById.get(mapping.getAccountClassItemId())))
        .filter(entry -> normalizedKeyword == null
            || (entry.getKey().getItemCode() + " " + entry.getKey().getItemName()
                + " " + entry.getValue().getItemCode() + " " + entry.getValue().getItemName())
                .toLowerCase(Locale.ROOT).contains(normalizedKeyword))
        .sorted((left, right) -> {
          int sort = Integer.compare(left.getKey().getSortOrder(), right.getKey().getSortOrder());
          return sort != 0 ? sort : left.getKey().getItemCode().compareTo(right.getKey().getItemCode());
        })
        .map(entry -> new AccountTypeOptionVo(
            entry.getKey().getItemCode(),
            entry.getKey().getItemName(),
            entry.getValue().getItemCode(),
            entry.getValue().getItemName()))
        .toList();
  }

  @Override
  public String resolveAccountClassCode(String accountTypeCode) {
    return resolveAccountClassCode(accountTypeCode, true);
  }

  @Override
  public String resolveAccountClassCode(String accountTypeCode, boolean requireEnabledType) {
    DictionaryItem typeItem = requireItemByCode(ACCOUNT_TYPE, accountTypeCode, requireEnabledType);
    AccountTypeClassMapping mapping = mappingMapper.selectById(typeItem.getDictionaryItemId());
    if (mapping == null) {
      throw new BusinessException(
          CommonErrorCode.DATA_CONFLICT,
          "账户类型未配置账户类别：" + typeItem.getItemCode());
    }
    DictionaryItem classItem = dictionaryItemMapper.selectById(mapping.getAccountClassItemId());
    DictionaryType classType = requireDictionaryType(ACCOUNT_CLASS);
    if (classItem == null
        || !classType.getDictionaryTypeId().equals(classItem.getDictionaryTypeId())
        || !Boolean.TRUE.equals(classItem.getEnabled())) {
      throw new BusinessException(
          CommonErrorCode.DATA_CONFLICT,
          "账户类型映射的账户类别不存在或已停用：" + typeItem.getItemCode());
    }
    return classItem.getItemCode();
  }

  @Override
  @Transactional
  public int upsertMapping(String accountTypeCode, String accountClassCode) {
    DictionaryItem typeItem = requireItemByCode(ACCOUNT_TYPE, accountTypeCode, false);
    DictionaryItem classItem = requireItemByCode(ACCOUNT_CLASS, accountClassCode, true);
    AccountTypeClassMapping mapping = mappingMapper.selectById(typeItem.getDictionaryItemId());
    Instant now = Instant.now();
    if (mapping == null) {
      mapping = new AccountTypeClassMapping();
      mapping.setAccountTypeItemId(typeItem.getDictionaryItemId());
      mapping.setAccountClassItemId(classItem.getDictionaryItemId());
      mapping.setCreatedAt(now);
      mapping.setUpdatedAt(now);
      return mappingMapper.insert(mapping);
    }
    if (!mapping.getAccountClassItemId().equals(classItem.getDictionaryItemId())) {
      Long accountCount = accountMapper.selectCount(
          Wrappers.<Account>lambdaQuery().eq(Account::getAccountTypeCode, typeItem.getItemCode()));
      if (accountCount > 0) {
        throw new BusinessException(
            CommonErrorCode.DATA_CONFLICT,
            "账户类型已被账户使用，不能直接修改账户类别映射；请通过显式数据迁移处理："
                + typeItem.getItemCode());
      }
    }
    mapping.setAccountClassItemId(classItem.getDictionaryItemId());
    mapping.setUpdatedAt(now);
    return mappingMapper.updateById(mapping);
  }

  private DictionaryType requireDictionaryType(String typeCode) {
    DictionaryType type = dictionaryTypeMapper.selectOne(
        Wrappers.<DictionaryType>lambdaQuery().eq(DictionaryType::getTypeCode, typeCode));
    if (type == null) {
      throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "字典类型不存在：" + typeCode);
    }
    return type;
  }

  private DictionaryItem requireItemByCode(String typeCode, String itemCode, boolean requireEnabled) {
    if (!StringUtils.hasText(itemCode)) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "字典项编码不能为空");
    }
    DictionaryType type = requireDictionaryType(typeCode);
    String normalized = itemCode.trim().toUpperCase(Locale.ROOT);
    DictionaryItem item = dictionaryItemMapper.selectOne(
        Wrappers.<DictionaryItem>lambdaQuery()
            .eq(DictionaryItem::getDictionaryTypeId, type.getDictionaryTypeId())
            .eq(DictionaryItem::getItemCode, normalized));
    if (item == null) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, typeCode + " 字典项不存在：" + normalized);
    }
    if (requireEnabled && !Boolean.TRUE.equals(item.getEnabled())) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, typeCode + " 字典项已停用：" + normalized);
    }
    return item;
  }
}
