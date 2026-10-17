package com.jiajunxie.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.convert.DictionaryConvertServiceMapper;
import com.jiajunxie.entity.DictionaryItem;
import com.jiajunxie.entity.DictionaryType;
import com.jiajunxie.mapper.AccountMapper;
import com.jiajunxie.mapper.CategoryMapper;
import com.jiajunxie.mapper.DictionaryItemMapper;
import com.jiajunxie.mapper.DictionaryTypeMapper;
import com.jiajunxie.mapper.TransactionEntryMapper;
import com.jiajunxie.mapper.TransactionMapper;
import com.jiajunxie.vo.DictionaryItemVo;

@ExtendWith(MockitoExtension.class)
class DictionaryServiceImplTest {
  @Mock DictionaryTypeMapper dictionaryTypeMapper;
  @Mock DictionaryItemMapper dictionaryItemMapper;
  @Mock DictionaryConvertServiceMapper dictionaryConvertMapper;
  @Mock AccountMapper accountMapper;
  @Mock TransactionMapper transactionMapper;
  @Mock TransactionEntryMapper transactionEntryMapper;
  @Mock CategoryMapper categoryMapper;

  private DictionaryServiceImpl service() {
    return new DictionaryServiceImpl(
        dictionaryTypeMapper,
        dictionaryItemMapper,
        dictionaryConvertMapper,
        accountMapper,
        transactionMapper,
        transactionEntryMapper,
        categoryMapper);
  }

  @Test
  void updateRejectsSystemItemCodeChange() {
    DictionaryType type = type("ACCOUNT_TYPE");
    DictionaryItem item = item("i1", "BANK", true);
    when(dictionaryTypeMapper.selectOne(any())).thenReturn(type);
    when(dictionaryItemMapper.selectById("i1")).thenReturn(item);

    DictionaryItemVo request = itemVo("BANK_CUSTOM", "银行账户");
    BusinessException exception =
        assertThrows(
            BusinessException.class, () -> service().updateItem("ACCOUNT_TYPE", "i1", request));

    assertEquals(40900, exception.getCode());
    verify(dictionaryItemMapper, never()).updateById(any(DictionaryItem.class));
  }

  @Test
  void removeRejectsSystemItem() {
    DictionaryType type = type("ACCOUNT_TYPE");
    DictionaryItem item = item("i1", "BANK", true);
    when(dictionaryTypeMapper.selectOne(any())).thenReturn(type);
    when(dictionaryItemMapper.selectById("i1")).thenReturn(item);

    BusinessException exception =
        assertThrows(BusinessException.class, () -> service().removeItem("ACCOUNT_TYPE", "i1"));

    assertEquals(40900, exception.getCode());
    verify(dictionaryItemMapper, never()).deleteById("i1");
  }

  @Test
  void removeRejectsReferencedCustomAccountType() {
    DictionaryType type = type("ACCOUNT_TYPE");
    DictionaryItem item = item("i1", "CUSTOM_BANK", false);
    when(dictionaryTypeMapper.selectOne(any())).thenReturn(type);
    when(dictionaryItemMapper.selectById("i1")).thenReturn(item);
    when(accountMapper.selectCount(any())).thenReturn(1L);

    BusinessException exception =
        assertThrows(BusinessException.class, () -> service().removeItem("ACCOUNT_TYPE", "i1"));

    assertEquals(40900, exception.getCode());
    verify(dictionaryItemMapper, never()).deleteById("i1");
  }

  @Test
  void removeDeletesUnusedCustomItem() {
    DictionaryType type = type("ACCOUNT_TYPE");
    DictionaryItem item = item("i1", "CUSTOM_BANK", false);
    when(dictionaryTypeMapper.selectOne(any())).thenReturn(type);
    when(dictionaryItemMapper.selectById("i1")).thenReturn(item);
    when(accountMapper.selectCount(any())).thenReturn(0L);
    when(dictionaryItemMapper.deleteById("i1")).thenReturn(1);

    assertEquals(1, service().removeItem("ACCOUNT_TYPE", "i1"));
  }

  @Test
  void createCustomItemForcesSystemItemFalseAndNormalizesCode() {
    DictionaryType type = type("CUSTOM_TYPE");
    when(dictionaryTypeMapper.selectOne(any())).thenReturn(type);
    when(dictionaryItemMapper.selectCount(any())).thenReturn(0L);
    DictionaryItem entity = new DictionaryItem();
    when(dictionaryConvertMapper.toItemEntity(any(DictionaryItemVo.class))).thenReturn(entity);
    when(dictionaryItemMapper.insert(entity)).thenReturn(1);

    DictionaryItemVo request = itemVo(" custom_value ", "自定义值");
    request.setMetadataJson("{\"source\":\"manual\"}");
    assertEquals(1, service().createItem("CUSTOM_TYPE", request));

    assertEquals("CUSTOM_VALUE", entity.getItemCode());
    assertFalse(entity.getSystemItem());
    assertEquals("{\"source\":\"manual\"}", entity.getMetadataJson());
  }

  @Test
  void createRejectsInvalidMetadataJson() {
    DictionaryType type = type("CUSTOM_TYPE");
    when(dictionaryTypeMapper.selectOne(any())).thenReturn(type);

    DictionaryItemVo request = itemVo("VALUE", "值");
    request.setMetadataJson("{invalid}");
    BusinessException exception =
        assertThrows(BusinessException.class, () -> service().createItem("CUSTOM_TYPE", request));

    assertEquals(40000, exception.getCode());
  }

  private DictionaryType type(String code) {
    DictionaryType type = new DictionaryType();
    type.setDictionaryTypeId("t1");
    type.setTypeCode(code);
    type.setTypeName(code);
    type.setEnabled(true);
    return type;
  }

  private DictionaryItem item(String id, String code, boolean systemItem) {
    DictionaryItem item = new DictionaryItem();
    item.setDictionaryItemId(id);
    item.setDictionaryTypeId("t1");
    item.setItemCode(code);
    item.setItemName(code);
    item.setSystemItem(systemItem);
    item.setEnabled(true);
    item.setSortOrder(0);
    return item;
  }

  private DictionaryItemVo itemVo(String code, String name) {
    DictionaryItemVo vo = new DictionaryItemVo();
    vo.setItemCode(code);
    vo.setItemName(name);
    vo.setEnabled(true);
    vo.setSortOrder(0);
    return vo;
  }
}
