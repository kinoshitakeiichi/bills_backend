package com.jiajunxie.service;

import java.util.List;
import com.jiajunxie.dto.PageQuery;
import com.jiajunxie.vo.PageResultVo;
import com.jiajunxie.vo.DictionaryItemVo;
import com.jiajunxie.vo.DictionaryOptionVo;
import com.jiajunxie.vo.DictionaryTypeVo;

public interface DictionaryService {
  List<DictionaryTypeVo> listTypes(Boolean enabled);

  List<DictionaryItemVo> listItems(String typeCode, String keyword, Boolean enabled);

  PageResultVo<DictionaryItemVo> pageItems(String typeCode, PageQuery query, Boolean enabled);

  List<DictionaryOptionVo> listOptions(String typeCode, String keyword);

  int createType(DictionaryTypeVo type);

  int updateType(String typeCode, DictionaryTypeVo type);

  int setTypeEnabled(String typeCode, boolean enabled);

  int createItem(String typeCode, DictionaryItemVo item);

  int updateItem(String typeCode, String dictionaryItemId, DictionaryItemVo item);

  int setItemEnabled(String typeCode, String dictionaryItemId, boolean enabled);

  int removeItem(String typeCode, String dictionaryItemId);
}
