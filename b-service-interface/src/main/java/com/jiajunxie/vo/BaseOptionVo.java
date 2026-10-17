package com.jiajunxie.vo;

/**
 * 主数据选择器的公共最小契约。
 * value 是稳定 ID 或业务 code；label 仅用于展示，enabled 用于管理页面而非普通业务筛选。
 */
public record BaseOptionVo(String value, String label, Boolean enabled) {}
