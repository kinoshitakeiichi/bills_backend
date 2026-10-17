package com.jiajunxie.service.impl;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.common.exception.CommonErrorCode;
import com.jiajunxie.convert.DictionaryConvertServiceMapper;
import com.jiajunxie.dto.PageQuery;
import com.jiajunxie.entity.Account;
import com.jiajunxie.entity.Category;
import com.jiajunxie.entity.DictionaryItem;
import com.jiajunxie.entity.DictionaryType;
import com.jiajunxie.entity.Transaction;
import com.jiajunxie.entity.TransactionEntry;
import com.jiajunxie.mapper.AccountMapper;
import com.jiajunxie.mapper.CategoryMapper;
import com.jiajunxie.mapper.DictionaryItemMapper;
import com.jiajunxie.mapper.DictionaryTypeMapper;
import com.jiajunxie.mapper.TransactionEntryMapper;
import com.jiajunxie.mapper.TransactionMapper;
import com.jiajunxie.service.DictionaryService;
import com.jiajunxie.service.support.MasterDataPageSupport;
import com.jiajunxie.vo.DictionaryItemVo;
import com.jiajunxie.vo.DictionaryOptionVo;
import com.jiajunxie.vo.DictionaryTypeVo;
import com.jiajunxie.vo.PageResultVo;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DictionaryServiceImpl implements DictionaryService {

  private final DictionaryTypeMapper dictionaryTypeMapper;
  private final DictionaryItemMapper dictionaryItemMapper;
  private final DictionaryConvertServiceMapper dictionaryConvertMapper;
  private final AccountMapper accountMapper;
  private final TransactionMapper transactionMapper;
  private final TransactionEntryMapper transactionEntryMapper;
  private final CategoryMapper categoryMapper;

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Override
  public List<DictionaryTypeVo> listTypes(Boolean enabled) {
    List<DictionaryType> types =
        dictionaryTypeMapper.selectList(
            Wrappers.<DictionaryType>lambdaQuery()
                .eq(enabled != null, DictionaryType::getEnabled, enabled)
                .orderByAsc(DictionaryType::getTypeCode));

    List<DictionaryTypeVo> result = dictionaryConvertMapper.toTypeVos(types);
    for (DictionaryTypeVo type : result) {
      Long count =
          dictionaryItemMapper.selectCount(
              Wrappers.<DictionaryItem>lambdaQuery()
                  .eq(DictionaryItem::getDictionaryTypeId, type.getDictionaryTypeId()));
      type.setItemCount(count);
    }
    return result;
  }

  @Override
  public List<DictionaryItemVo> listItems(String typeCode, String keyword, Boolean enabled) {
    DictionaryType type = findType(typeCode);
    String normalizedKeyword = normalizeKeyword(keyword);

    List<DictionaryItem> items =
        dictionaryItemMapper.selectList(
            Wrappers.<DictionaryItem>lambdaQuery()
                .eq(DictionaryItem::getDictionaryTypeId, type.getDictionaryTypeId())
                .eq(enabled != null, DictionaryItem::getEnabled, enabled)
                .and(
                    StringUtils.hasText(normalizedKeyword),
                    wrapper ->
                        wrapper
                            .like(DictionaryItem::getItemCode, normalizedKeyword)
                            .or()
                            .like(DictionaryItem::getItemName, normalizedKeyword))
                .orderByAsc(DictionaryItem::getSortOrder)
                .orderByAsc(DictionaryItem::getItemCode));

    List<DictionaryItemVo> result = dictionaryConvertMapper.toItemVos(items);
    result.forEach(item -> item.setTypeCode(type.getTypeCode()));
    return result;
  }

  @Override
  public PageResultVo<DictionaryItemVo> pageItems(
      String typeCode, PageQuery query, Boolean enabled) {
    DictionaryType type = findType(typeCode);
    PageQuery safe = query == null ? new PageQuery(null, null, null, null, null) : query;
    String sortField =
        MasterDataPageSupport.allowedSortField(
            safe, Set.of("itemCode", "itemName", "sortOrder"), "sortOrder");
    boolean asc = !MasterDataPageSupport.descending(safe);

    var wrapper =
        Wrappers.<DictionaryItem>lambdaQuery()
            .eq(DictionaryItem::getDictionaryTypeId, type.getDictionaryTypeId())
            .eq(enabled != null, DictionaryItem::getEnabled, enabled)
            .and(
                StringUtils.hasText(safe.normalizedKeyword()),
                w ->
                    w.like(DictionaryItem::getItemCode, safe.normalizedKeyword())
                        .or()
                        .like(DictionaryItem::getItemName, safe.normalizedKeyword()));
    switch (sortField) {
      case "itemCode" -> wrapper.orderBy(true, asc, DictionaryItem::getItemCode);
      case "itemName" -> wrapper.orderBy(true, asc, DictionaryItem::getItemName);
      default ->
          wrapper
              .orderBy(true, asc, DictionaryItem::getSortOrder)
              .orderByAsc(DictionaryItem::getItemCode);
    }

    var page = dictionaryItemMapper.selectPage(MasterDataPageSupport.page(safe), wrapper);
    return MasterDataPageSupport.result(
        page,
        item -> {
          DictionaryItemVo vo = dictionaryConvertMapper.toItemVo(item);
          vo.setTypeCode(type.getTypeCode());
          return vo;
        });
  }

  @Override
  public List<DictionaryOptionVo> listOptions(String typeCode, String keyword) {
    DictionaryType type = findType(typeCode);
    if (!Boolean.TRUE.equals(type.getEnabled())) {
      return List.of();
    }
    return listItems(typeCode, keyword, true).stream()
        .map(item -> new DictionaryOptionVo(item.getItemCode(), item.getItemName()))
        .toList();
  }

  @Override
  @Transactional
  public int createType(DictionaryTypeVo typeVo) {
    validateType(typeVo);
    String typeCode = normalizeCode(typeVo.getTypeCode());
    ensureTypeCodeUnique(typeCode, null);
    typeVo.setTypeCode(typeCode);

    DictionaryType entity = dictionaryConvertMapper.toTypeEntity(typeVo);
    Instant now = Instant.now();
    entity.setDictionaryTypeId(UUID.randomUUID().toString());
    entity.setCreatedAt(now);
    entity.setUpdatedAt(now);
    if (entity.getEnabled() == null) entity.setEnabled(true);
    return dictionaryTypeMapper.insert(entity);
  }

  @Override
  @Transactional
  public int updateType(String typeCode, DictionaryTypeVo typeVo) {
    DictionaryType entity = findType(typeCode);
    validateType(typeVo);
    String requestedCode = normalizeCode(typeVo.getTypeCode());
    if (!entity.getTypeCode().equals(requestedCode)) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "字典类型编码创建后不可修改");
    }
    dictionaryConvertMapper.updateTypeEntity(typeVo, entity);
    entity.setTypeCode(requestedCode);
    entity.setTypeName(typeVo.getTypeName().trim());
    entity.setDescription(trimToNull(typeVo.getDescription()));
    entity.setEnabled(typeVo.getEnabled() == null ? Boolean.TRUE : typeVo.getEnabled());
    entity.setUpdatedAt(Instant.now());
    return dictionaryTypeMapper.updateById(entity);
  }

  @Override
  @Transactional
  public int setTypeEnabled(String typeCode, boolean enabled) {
    DictionaryType entity = findType(typeCode);
    entity.setEnabled(enabled);
    entity.setUpdatedAt(Instant.now());
    return dictionaryTypeMapper.updateById(entity);
  }

  @Override
  @Transactional
  public int createItem(String typeCode, DictionaryItemVo itemVo) {
    DictionaryType type = findType(typeCode);
    validateItem(itemVo);
    String itemCode = normalizeCode(itemVo.getItemCode());
    ensureItemCodeUnique(type.getDictionaryTypeId(), itemCode, null);
    itemVo.setItemCode(itemCode);

    DictionaryItem entity = dictionaryConvertMapper.toItemEntity(itemVo);
    Instant now = Instant.now();
    entity.setDictionaryItemId(UUID.randomUUID().toString());
    entity.setDictionaryTypeId(type.getDictionaryTypeId());
    entity.setSystemItem(false);
    entity.setItemCode(itemCode);
    entity.setItemName(itemVo.getItemName().trim());
    entity.setDescription(trimToNull(itemVo.getDescription()));
    entity.setMetadataJson(normalizeMetadata(itemVo.getMetadataJson()));
    entity.setSortOrder(itemVo.getSortOrder() == null ? 0 : itemVo.getSortOrder());
    entity.setEnabled(itemVo.getEnabled() == null ? Boolean.TRUE : itemVo.getEnabled());
    entity.setCreatedAt(now);
    entity.setUpdatedAt(now);
    return dictionaryItemMapper.insert(entity);
  }

  @Override
  @Transactional
  public int updateItem(String typeCode, String dictionaryItemId, DictionaryItemVo itemVo) {
    DictionaryType type = findType(typeCode);
    DictionaryItem entity = requireItem(type, dictionaryItemId);
    validateItem(itemVo);

    String requestedCode = normalizeCode(itemVo.getItemCode());
    String existingCode = normalizeCode(entity.getItemCode());
    if (!existingCode.equals(requestedCode)) {
      if (Boolean.TRUE.equals(entity.getSystemItem())) {
        throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "系统字典项编码不可修改");
      }
      ensureItemCodeUnused(type.getTypeCode(), existingCode);
      ensureItemCodeUnique(type.getDictionaryTypeId(), requestedCode, dictionaryItemId);
    }

    dictionaryConvertMapper.updateItemEntity(itemVo, entity);
    entity.setItemCode(requestedCode);
    entity.setItemName(itemVo.getItemName().trim());
    entity.setDescription(trimToNull(itemVo.getDescription()));
    entity.setMetadataJson(normalizeMetadata(itemVo.getMetadataJson()));
    entity.setSortOrder(itemVo.getSortOrder() == null ? 0 : itemVo.getSortOrder());
    entity.setEnabled(itemVo.getEnabled() == null ? Boolean.TRUE : itemVo.getEnabled());
    entity.setUpdatedAt(Instant.now());
    return dictionaryItemMapper.updateById(entity);
  }

  @Override
  @Transactional
  public int setItemEnabled(String typeCode, String dictionaryItemId, boolean enabled) {
    DictionaryType type = findType(typeCode);
    DictionaryItem entity = requireItem(type, dictionaryItemId);
    entity.setEnabled(enabled);
    entity.setUpdatedAt(Instant.now());
    return dictionaryItemMapper.updateById(entity);
  }

  @Override
  @Transactional
  public int removeItem(String typeCode, String dictionaryItemId) {
    DictionaryType type = findType(typeCode);
    DictionaryItem entity = requireItem(type, dictionaryItemId);
    if (Boolean.TRUE.equals(entity.getSystemItem())) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "系统字典项不能删除");
    }
    ensureItemCodeUnused(type.getTypeCode(), normalizeCode(entity.getItemCode()));
    return dictionaryItemMapper.deleteById(dictionaryItemId);
  }

  private DictionaryType findType(String typeCode) {
    if (!StringUtils.hasText(typeCode)) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "字典类型编码不能为空");
    }

    DictionaryType type =
        dictionaryTypeMapper.selectOne(
            Wrappers.<DictionaryType>lambdaQuery()
                .eq(DictionaryType::getTypeCode, normalizeCode(typeCode)));

    if (type == null) {
      throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "字典类型不存在：" + typeCode);
    }
    return type;
  }

  private DictionaryItem requireItem(DictionaryType type, String dictionaryItemId) {
    if (!StringUtils.hasText(dictionaryItemId)) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "字典项ID不能为空");
    }
    DictionaryItem item = dictionaryItemMapper.selectById(dictionaryItemId);
    if (item == null || !type.getDictionaryTypeId().equals(item.getDictionaryTypeId())) {
      throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "字典项不存在：" + dictionaryItemId);
    }
    return item;
  }

  private void validateType(DictionaryTypeVo typeVo) {
    if (typeVo == null
        || !StringUtils.hasText(typeVo.getTypeCode())
        || !StringUtils.hasText(typeVo.getTypeName())) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "字典类型编码和名称不能为空");
    }
    typeVo.setTypeCode(normalizeCode(typeVo.getTypeCode()));
    typeVo.setTypeName(typeVo.getTypeName().trim());
    typeVo.setDescription(trimToNull(typeVo.getDescription()));
    if (typeVo.getEnabled() == null) typeVo.setEnabled(true);
  }

  private void validateItem(DictionaryItemVo itemVo) {
    if (itemVo == null
        || !StringUtils.hasText(itemVo.getItemCode())
        || !StringUtils.hasText(itemVo.getItemName())) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "字典项编码和名称不能为空");
    }
    itemVo.setItemCode(normalizeCode(itemVo.getItemCode()));
    itemVo.setItemName(itemVo.getItemName().trim());
    itemVo.setDescription(trimToNull(itemVo.getDescription()));
    itemVo.setMetadataJson(normalizeMetadata(itemVo.getMetadataJson()));
    if (itemVo.getEnabled() == null) itemVo.setEnabled(true);
    if (itemVo.getSortOrder() == null) itemVo.setSortOrder(0);
  }

  private void ensureTypeCodeUnique(String typeCode, String excludedTypeId) {
    var wrapper =
        Wrappers.<DictionaryType>lambdaQuery()
            .eq(DictionaryType::getTypeCode, typeCode)
            .ne(
                StringUtils.hasText(excludedTypeId),
                DictionaryType::getDictionaryTypeId,
                excludedTypeId);
    if (dictionaryTypeMapper.selectCount(wrapper) > 0) {
      throw new BusinessException(CommonErrorCode.DUPLICATE_DATA, "字典类型编码已存在：" + typeCode);
    }
  }

  private void ensureItemCodeUnique(
      String dictionaryTypeId, String itemCode, String excludedItemId) {
    var wrapper =
        Wrappers.<DictionaryItem>lambdaQuery()
            .eq(DictionaryItem::getDictionaryTypeId, dictionaryTypeId)
            .eq(DictionaryItem::getItemCode, itemCode)
            .ne(
                StringUtils.hasText(excludedItemId),
                DictionaryItem::getDictionaryItemId,
                excludedItemId);
    if (dictionaryItemMapper.selectCount(wrapper) > 0) {
      throw new BusinessException(CommonErrorCode.DUPLICATE_DATA, "字典项编码已存在：" + itemCode);
    }
  }

  /**
   * Business tables store stable dictionary codes rather than dictionary item IDs. Therefore
   * changing or deleting a non-system item is only safe when the old code is not referenced by a
   * currently implemented core domain table. Future features extend this switch when they start
   * persisting additional code fields.
   */
  private void ensureItemCodeUnused(String typeCode, String itemCode) {
    long count =
        switch (typeCode) {
          case "ACCOUNT_CLASS" ->
              accountMapper.selectCount(
                  Wrappers.<Account>lambdaQuery().eq(Account::getAccountClassCode, itemCode));
          case "ACCOUNT_TYPE" ->
              accountMapper.selectCount(
                  Wrappers.<Account>lambdaQuery().eq(Account::getAccountTypeCode, itemCode));
          case "TRANSACTION_TYPE" ->
              transactionMapper.selectCount(
                  Wrappers.<Transaction>lambdaQuery()
                      .eq(Transaction::getTransactionType, itemCode));
          case "ENTRY_ROLE" ->
              transactionEntryMapper.selectCount(
                  Wrappers.<TransactionEntry>lambdaQuery()
                      .eq(TransactionEntry::getEntryRole, itemCode));
          case "CATEGORY_TYPE" ->
              categoryMapper.selectCount(
                  Wrappers.<Category>lambdaQuery().eq(Category::getCategoryTypeCode, itemCode));
          default -> 0L;
        };
    if (count > 0) {
      throw new BusinessException(
          CommonErrorCode.DATA_CONFLICT, "字典项编码已被业务数据引用，请停用而不是改码或删除：" + itemCode);
    }
  }

  private String normalizeMetadata(String metadataJson) {
    String normalized = trimToNull(metadataJson);
    if (normalized == null) return null;
    try {
      return OBJECT_MAPPER.readTree(normalized).toString();
    } catch (Exception exception) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "metadataJson 必须是合法 JSON");
    }
  }

  private String normalizeKeyword(String keyword) {
    return StringUtils.hasText(keyword) ? keyword.trim() : null;
  }

  private String normalizeCode(String value) {
    return value.trim().toUpperCase(Locale.ROOT);
  }

  private String trimToNull(String value) {
    return StringUtils.hasText(value) ? value.trim() : null;
  }
}
