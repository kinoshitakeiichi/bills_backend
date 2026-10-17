package com.jiajunxie.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.common.exception.CommonErrorCode;
import com.jiajunxie.entity.AccountTransactionTemplate;
import com.jiajunxie.mapper.AccountMapper;
import com.jiajunxie.mapper.AccountTransactionTemplateMapper;
import com.jiajunxie.service.AccountTransactionTemplateService;
import com.jiajunxie.vo.AccountTransactionTemplateSaveVo;
import com.jiajunxie.vo.AccountTransactionTemplateVo;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AccountTransactionTemplateServiceImpl implements AccountTransactionTemplateService {
  private final AccountTransactionTemplateMapper templateMapper;
  private final AccountMapper accountMapper;

  @Override
  public List<AccountTransactionTemplateVo> list(String accountId) {
    requireAccount(accountId);
    return templateMapper.selectList(
        Wrappers.<AccountTransactionTemplate>lambdaQuery()
            .eq(AccountTransactionTemplate::getAccountId, accountId)
            .eq(AccountTransactionTemplate::getEnabled, true)
            .orderByAsc(AccountTransactionTemplate::getSortOrder)
            .orderByAsc(AccountTransactionTemplate::getCreatedAt))
        .stream().map(this::toVo).toList();
  }

  @Override
  @Transactional
  public String create(String accountId, AccountTransactionTemplateSaveVo request) {
    requireAccount(accountId);
    validate(request);
    ensureNameUnique(accountId, null, request.templateName().trim());
    AccountTransactionTemplate entity = new AccountTransactionTemplate();
    entity.setTemplateId(UUID.randomUUID().toString());
    entity.setAccountId(accountId);
    apply(entity, request);
    Instant now = Instant.now();
    entity.setCreatedAt(now);
    entity.setUpdatedAt(now);
    templateMapper.insert(entity);
    return entity.getTemplateId();
  }

  @Override
  @Transactional
  public int update(String accountId, String templateId, AccountTransactionTemplateSaveVo request) {
    AccountTransactionTemplate entity = requireTemplate(accountId, templateId);
    validate(request);
    ensureNameUnique(accountId, templateId, request.templateName().trim());
    apply(entity, request);
    entity.setUpdatedAt(Instant.now());
    return templateMapper.updateById(entity);
  }

  @Override
  @Transactional
  public int remove(String accountId, String templateId) {
    return templateMapper.deleteById(requireTemplate(accountId, templateId).getTemplateId());
  }

  private void apply(AccountTransactionTemplate entity, AccountTransactionTemplateSaveVo request) {
    entity.setTemplateName(request.templateName().trim());
    entity.setPresetJson(request.presetJson());
    entity.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
    entity.setEnabled(request.enabled() == null ? true : request.enabled());
  }

  private void validate(AccountTransactionTemplateSaveVo request) {
    if (request == null || !StringUtils.hasText(request.templateName())) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "快捷按钮名称不能为空");
    }
    if (request.templateName().trim().length() > 100) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "快捷按钮名称不能超过100个字符");
    }
    if (!StringUtils.hasText(request.presetJson())) {
      throw new BusinessException(CommonErrorCode.PARAMETER_ERROR, "交易模板不能为空");
    }
  }

  private void requireAccount(String accountId) {
    if (!StringUtils.hasText(accountId) || accountMapper.selectById(accountId) == null) {
      throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "账户不存在");
    }
  }

  private AccountTransactionTemplate requireTemplate(String accountId, String templateId) {
    AccountTransactionTemplate entity = templateMapper.selectOne(
        Wrappers.<AccountTransactionTemplate>lambdaQuery()
            .eq(AccountTransactionTemplate::getTemplateId, templateId)
            .eq(AccountTransactionTemplate::getAccountId, accountId));
    if (entity == null) throw new BusinessException(CommonErrorCode.DATA_NOT_FOUND, "快捷交易模板不存在");
    return entity;
  }

  private void ensureNameUnique(String accountId, String excludeId, String name) {
    var query = Wrappers.<AccountTransactionTemplate>lambdaQuery()
        .eq(AccountTransactionTemplate::getAccountId, accountId)
        .eq(AccountTransactionTemplate::getTemplateName, name);
    if (StringUtils.hasText(excludeId)) query.ne(AccountTransactionTemplate::getTemplateId, excludeId);
    if (templateMapper.selectCount(query) > 0) {
      throw new BusinessException(CommonErrorCode.DATA_CONFLICT, "同一账户下快捷按钮名称不能重复");
    }
  }

  private AccountTransactionTemplateVo toVo(AccountTransactionTemplate entity) {
    return new AccountTransactionTemplateVo(entity.getTemplateId(), entity.getAccountId(), entity.getTemplateName(),
        entity.getPresetJson(), entity.getSortOrder(), entity.getEnabled());
  }
}
