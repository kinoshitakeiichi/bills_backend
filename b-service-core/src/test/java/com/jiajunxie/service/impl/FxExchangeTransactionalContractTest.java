package com.jiajunxie.service.impl;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jiajunxie.vo.FxExchangeCreateVo;
import jakarta.transaction.Transactional;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class FxExchangeTransactionalContractTest {

  @Test
  void createRunsInsideOneTransactionalBoundary() throws Exception {
    Method method = FxExchangeServiceImpl.class.getMethod("create", FxExchangeCreateVo.class);
    Transactional transactional = method.getAnnotation(Transactional.class);

    assertNotNull(transactional);
    // BusinessException is a RuntimeException; Jakarta/Spring transaction semantics roll back
    // runtime failures by default. No dontRollbackOn override is declared.
    assertTrue(transactional.dontRollbackOn().length == 0);
  }
}
