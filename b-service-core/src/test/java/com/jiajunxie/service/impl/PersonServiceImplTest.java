package com.jiajunxie.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.convert.PersonConvertServiceMapper;
import com.jiajunxie.entity.Person;
import com.jiajunxie.mapper.PersonMapper;
import com.jiajunxie.vo.PersonVo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PersonServiceImplTest {
  @Mock PersonMapper personMapper;
  @Mock PersonConvertServiceMapper personConvertMapper;

  @Test
  void createRejectsDuplicatePersonCode() {
    var service = new PersonServiceImpl(personMapper, personConvertMapper);
    var vo = new PersonVo();
    vo.setPersonCode("ME");
    vo.setPersonName("我");
    vo.setEnabled(true);
    vo.setSortOrder(0);
    when(personMapper.selectCount(any())).thenReturn(1L);

    BusinessException exception = assertThrows(BusinessException.class, () -> service.createPerson(vo));
    assertEquals(40901, exception.getCode());
  }

  @Test
  void setEnabledRejectsMissingPerson() {
    var service = new PersonServiceImpl(personMapper, personConvertMapper);
    when(personMapper.selectById("missing")).thenReturn(null);

    BusinessException exception = assertThrows(BusinessException.class, () -> service.setEnabled("missing", false));
    assertEquals(40400, exception.getCode());
  }

  @Test
  void setEnabledUpdatesExistingPersonWithoutDeletingIt() {
    var service = new PersonServiceImpl(personMapper, personConvertMapper);
    var entity = new Person();
    entity.setPersonId("p1");
    entity.setEnabled(true);
    when(personMapper.selectById("p1")).thenReturn(entity);
    when(personMapper.updateById(entity)).thenReturn(1);

    assertEquals(1, service.setEnabled("p1", false));
    assertEquals(false, entity.getEnabled());
  }
}
