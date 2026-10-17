package com.jiajunxie.service;

import com.jiajunxie.dto.PageQuery;
import com.jiajunxie.vo.PageResultVo;
import com.jiajunxie.vo.PersonOptionVo;
import com.jiajunxie.vo.PersonVo;
import java.util.List;

public interface PersonService {
  List<PersonVo> listPersons();

  PageResultVo<PersonVo> pagePersons(PageQuery query, Boolean enabled);

  List<PersonOptionVo> listOptions();

  int createPerson(PersonVo person);

  int updatePerson(String personId, PersonVo person);

  int setEnabled(String personId, boolean enabled);
}
