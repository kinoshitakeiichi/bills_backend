package com.jiajunxie.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.common.exception.CommonErrorCode;
import com.jiajunxie.convert.PersonConvertServiceMapper;
import com.jiajunxie.dto.PageQuery;
import com.jiajunxie.entity.Person;
import com.jiajunxie.mapper.PersonMapper;
import com.jiajunxie.service.PersonService;
import com.jiajunxie.service.support.MasterDataPageSupport;
import com.jiajunxie.vo.PageResultVo;
import com.jiajunxie.vo.PersonOptionVo;
import com.jiajunxie.vo.PersonVo;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class PersonServiceImpl implements PersonService {
  private final PersonMapper personMapper;
  private final PersonConvertServiceMapper personConvertMapper;

  @Override
  public List<PersonVo> listPersons() {
    return personConvertMapper.toVos(personMapper.selectList(
        Wrappers.<Person>lambdaQuery()
            .orderByAsc(Person::getSortOrder)
            .orderByAsc(Person::getPersonName)));
  }

  @Override
  public PageResultVo<PersonVo> pagePersons(PageQuery query, Boolean enabled) {
    PageQuery safe = query == null ? new PageQuery(null, null, null, null, null) : query;
    String sortField = MasterDataPageSupport.allowedSortField(
        safe, Set.of("personCode", "personName", "sortOrder", "createdAt", "updatedAt"), "sortOrder");
    boolean asc = !MasterDataPageSupport.descending(safe);

    var wrapper = Wrappers.<Person>lambdaQuery()
        .eq(enabled != null, Person::getEnabled, enabled)
        .and(StringUtils.hasText(safe.normalizedKeyword()), w -> w
            .like(Person::getPersonCode, safe.normalizedKeyword())
            .or()
            .like(Person::getPersonName, safe.normalizedKeyword()));
    switch (sortField) {
      case "personCode" -> wrapper.orderBy(true, asc, Person::getPersonCode);
      case "personName" -> wrapper.orderBy(true, asc, Person::getPersonName);
      case "createdAt" -> wrapper.orderBy(true, asc, Person::getCreatedAt);
      case "updatedAt" -> wrapper.orderBy(true, asc, Person::getUpdatedAt);
      default -> wrapper.orderBy(true, asc, Person::getSortOrder)
          .orderByAsc(Person::getPersonName);
    }

    var page = personMapper.selectPage(MasterDataPageSupport.page(safe), wrapper);
    return MasterDataPageSupport.result(page, personConvertMapper::toVo);
  }

  @Override
  public List<PersonOptionVo> listOptions() {
    return personMapper.selectList(
            Wrappers.<Person>lambdaQuery()
                .eq(Person::getEnabled, true)
                .orderByAsc(Person::getSortOrder)
                .orderByAsc(Person::getPersonName))
        .stream()
        .map(item -> new PersonOptionVo(item.getPersonId(), item.getPersonName()))
        .toList();
  }

  @Override
  @Transactional
  public int createPerson(PersonVo personVo) {
    validate(personVo);
    ensureCodeUnique(personVo.getPersonCode(), null);
    Person entity = personConvertMapper.toEntity(personVo);
    Instant now = Instant.now();
    entity.setPersonId(UUID.randomUUID().toString());
    entity.setCreatedAt(now);
    entity.setUpdatedAt(now);
    return personMapper.insert(entity);
  }

  @Override
  @Transactional
  public int updatePerson(String personId, PersonVo personVo) {
    if (!StringUtils.hasText(personId)) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "人员ID不能为空");
    }
    validate(personVo);
    Person entity = personMapper.selectById(personId);
    if (entity == null) {
      throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "人员不存在：" + personId);
    }
    ensureCodeUnique(personVo.getPersonCode(), personId);
    personConvertMapper.updateEntity(personVo, entity);
    entity.setPersonId(personId);
    entity.setUpdatedAt(Instant.now());
    return personMapper.updateById(entity);
  }

  @Override
  @Transactional
  public int setEnabled(String personId, boolean enabled) {
    if (!StringUtils.hasText(personId)) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "人员ID不能为空");
    }
    Person entity = personMapper.selectById(personId);
    if (entity == null) {
      throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "人员不存在：" + personId);
    }
    // Person is historical master data: normal maintenance changes availability instead of deleting rows.
    entity.setEnabled(enabled);
    entity.setUpdatedAt(Instant.now());
    return personMapper.updateById(entity);
  }

  private void ensureCodeUnique(String personCode, String excludedPersonId) {
    var wrapper = Wrappers.<Person>lambdaQuery()
        .eq(Person::getPersonCode, personCode)
        .ne(StringUtils.hasText(excludedPersonId), Person::getPersonId, excludedPersonId);
    if (personMapper.selectCount(wrapper) > 0) {
      throw new BusinessException(CommonErrorCode.DUPLICATE_DATA, "人员编码已存在：" + personCode);
    }
  }

  private void validate(PersonVo personVo) {
    if (personVo == null || !StringUtils.hasText(personVo.getPersonCode())
        || !StringUtils.hasText(personVo.getPersonName())) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "人员编码和名称不能为空");
    }
    personVo.setPersonCode(personVo.getPersonCode().trim());
    personVo.setPersonName(personVo.getPersonName().trim());
    if (personVo.getEnabled() == null) personVo.setEnabled(true);
    if (personVo.getSortOrder() == null) personVo.setSortOrder(0);
  }
}
