package com.jiajunxie.vo;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.Builder;

@Builder
public record BarkNotificationVo(
    String title,
    String body,
    String url,
    String sound,
    Integer call,
    String group,
    String icon,
    String level,
    Integer isArchive,
    Double volume) {
  public Map<String, Object> toPayload() {

    Map<String, Object> payload = new HashMap<>();
    payload.put("title", title);
    payload.put("body", body);
    Optional.ofNullable(url).ifPresent(v -> payload.put("url", v));
    Optional.ofNullable(sound).ifPresent(v -> payload.put("sound", v));
    Optional.ofNullable(call).ifPresent(v -> payload.put("call", v));
    Optional.ofNullable(group).ifPresent(v -> payload.put("group", v));
    Optional.ofNullable(icon).ifPresent(v -> payload.put("icon", v));
    Optional.ofNullable(level).ifPresent(v -> payload.put("level", v));
    Optional.ofNullable(isArchive).ifPresent(v -> payload.put("isArchive", v));
    Optional.ofNullable(volume).ifPresent(v -> payload.put("volume", v));
    return payload;
  }
}
