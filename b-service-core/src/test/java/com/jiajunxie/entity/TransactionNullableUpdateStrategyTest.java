package com.jiajunxie.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class TransactionNullableUpdateStrategyTest {

  @Test
  void editableNullableFieldsMustPersistExplicitNullOnUpdate() throws Exception {
    assertAlways("categoryId");
    assertAlways("merchant");
    assertAlways("remark");
  }

  private void assertAlways(String fieldName) throws Exception {
    Field field = Transaction.class.getDeclaredField(fieldName);
    TableField tableField = field.getAnnotation(TableField.class);
    assertEquals(
        FieldStrategy.ALWAYS,
        tableField.updateStrategy(),
        () -> fieldName + " must not silently ignore an explicit null during updateById");
  }
}
