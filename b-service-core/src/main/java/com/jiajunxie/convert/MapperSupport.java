package com.jiajunxie.convert;

import org.mapstruct.Named;

public interface MapperSupport {

  @Named("emptyToNull")
  default String emptyToNull(String value) {
    if (value == null) {
      return null;
    }

    String result = value.trim();
    return result.isEmpty() ? null : result;
  }
}
