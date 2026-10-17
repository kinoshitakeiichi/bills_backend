package com.jiajunxie.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.entity.AccountTypeClassMapping;
import com.jiajunxie.entity.DictionaryItem;
import com.jiajunxie.entity.DictionaryType;
import com.jiajunxie.mapper.AccountMapper;
import com.jiajunxie.mapper.AccountTypeClassMappingMapper;
import com.jiajunxie.mapper.DictionaryItemMapper;
import com.jiajunxie.mapper.DictionaryTypeMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountTypeMappingServiceImplTest {

  @Mock DictionaryTypeMapper dictionaryTypeMapper;
  @Mock DictionaryItemMapper dictionaryItemMapper;
  @Mock AccountTypeClassMappingMapper mappingMapper;
  @Mock AccountMapper accountMapper;

  private AccountTypeMappingServiceImpl service() {
    return new AccountTypeMappingServiceImpl(dictionaryTypeMapper, dictionaryItemMapper, mappingMapper, accountMapper);
  }

  @Test
  void resolveUsesStructuredMappingInsteadOfHardcodedSwitch() {
    DictionaryType accountType = type("t-type", "ACCOUNT_TYPE");
    DictionaryType accountClass = type("t-class", "ACCOUNT_CLASS");
    DictionaryItem creditCard = item("i-credit", "t-type", "CREDIT_CARD", true);
    DictionaryItem liability = item("i-liability", "t-class", "LIABILITY", true);
    AccountTypeClassMapping mapping = mapping("i-credit", "i-liability");

    when(dictionaryTypeMapper.selectOne(any())).thenReturn(accountType, accountClass);
    when(dictionaryItemMapper.selectOne(any())).thenReturn(creditCard);
    when(mappingMapper.selectById("i-credit")).thenReturn(mapping);
    when(dictionaryItemMapper.selectById("i-liability")).thenReturn(liability);

    assertEquals("LIABILITY", service().resolveAccountClassCode("credit_card"));
  }

  @Test
  void resolveRejectsUnmappedAccountType() {
    DictionaryType accountType = type("t-type", "ACCOUNT_TYPE");
    DictionaryItem custom = item("i-custom", "t-type", "CUSTOM", true);
    when(dictionaryTypeMapper.selectOne(any())).thenReturn(accountType);
    when(dictionaryItemMapper.selectOne(any())).thenReturn(custom);
    when(mappingMapper.selectById("i-custom")).thenReturn(null);

    BusinessException exception = assertThrows(
        BusinessException.class,
        () -> service().resolveAccountClassCode("CUSTOM"));

    assertEquals(40900, exception.getCode());
  }

  @Test
  void upsertCreatesMappingWithDictionaryItemForeignKeys() {
    DictionaryType accountType = type("t-type", "ACCOUNT_TYPE");
    DictionaryType accountClass = type("t-class", "ACCOUNT_CLASS");
    DictionaryItem bank = item("i-bank", "t-type", "BANK", true);
    DictionaryItem asset = item("i-asset", "t-class", "ASSET", true);

    when(dictionaryTypeMapper.selectOne(any())).thenReturn(accountType, accountClass);
    when(dictionaryItemMapper.selectOne(any())).thenReturn(bank, asset);
    when(mappingMapper.selectById("i-bank")).thenReturn(null);
    when(mappingMapper.insert(any(AccountTypeClassMapping.class))).thenReturn(1);

    assertEquals(1, service().upsertMapping("BANK", "ASSET"));
    verify(mappingMapper).insert(any(AccountTypeClassMapping.class));
  }

  @Test
  void upsertRejectsClassChangeAfterAccountTypeIsUsed() {
    DictionaryType accountType = type("t-type", "ACCOUNT_TYPE");
    DictionaryType accountClass = type("t-class", "ACCOUNT_CLASS");
    DictionaryItem bank = item("i-bank", "t-type", "BANK", true);
    DictionaryItem liability = item("i-liability", "t-class", "LIABILITY", true);
    AccountTypeClassMapping existing = mapping("i-bank", "i-asset");

    when(dictionaryTypeMapper.selectOne(any())).thenReturn(accountType, accountClass);
    when(dictionaryItemMapper.selectOne(any())).thenReturn(bank, liability);
    when(mappingMapper.selectById("i-bank")).thenReturn(existing);
    when(accountMapper.selectCount(any())).thenReturn(1L);

    BusinessException exception = assertThrows(
        BusinessException.class,
        () -> service().upsertMapping("BANK", "LIABILITY"));

    assertEquals(40900, exception.getCode());
  }

  private DictionaryType type(String id, String code) {
    DictionaryType type = new DictionaryType();
    type.setDictionaryTypeId(id);
    type.setTypeCode(code);
    type.setEnabled(true);
    return type;
  }

  private DictionaryItem item(String id, String typeId, String code, boolean enabled) {
    DictionaryItem item = new DictionaryItem();
    item.setDictionaryItemId(id);
    item.setDictionaryTypeId(typeId);
    item.setItemCode(code);
    item.setItemName(code);
    item.setEnabled(enabled);
    item.setSortOrder(0);
    return item;
  }

  private AccountTypeClassMapping mapping(String typeItemId, String classItemId) {
    AccountTypeClassMapping mapping = new AccountTypeClassMapping();
    mapping.setAccountTypeItemId(typeItemId);
    mapping.setAccountClassItemId(classItemId);
    return mapping;
  }
}
