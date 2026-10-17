package com.jiajunxie.convert;

import java.util.Locale;
import org.mapstruct.Named;

public interface WebMapperSupport {

  @Named("trim")
  default String trim(String value) {
    return value == null ? null : value.trim();
  }

  @Named("upperTrim")
  default String upperTrim(String value) {
    return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
  }

  @Named("emptyToNull")
  default String emptyToNull(String value) {
    if (value == null) {
      return null;
    }

    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }
}
