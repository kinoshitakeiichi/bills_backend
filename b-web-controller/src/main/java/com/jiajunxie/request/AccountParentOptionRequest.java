package com.jiajunxie.request;

import lombok.Data;

@Data
public class AccountParentOptionRequest {
  private String mainAccountId;
  private String currency;
  private String parentAccountIdId;
}
