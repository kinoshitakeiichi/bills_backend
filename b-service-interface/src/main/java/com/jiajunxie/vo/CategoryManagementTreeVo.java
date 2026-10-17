package com.jiajunxie.vo;

import java.util.List;

/**
 * Category tree projection used by the master-data maintenance screen.
 *
 * <p>Unlike the business selection tree, this projection also contains disabled nodes and the
 * maintenance fields required by the editor. Business transaction forms must continue using
 * {@link CategoryTreeVo}, which exposes enabled selectable categories only.
 */
public record CategoryManagementTreeVo(
    String value,
    String label,
    String categoryCode,
    String categoryTypeCode,
    String iconCode,
    String parentId,
    Integer sortOrder,
    boolean enabled,
    boolean leaf,
    List<CategoryManagementTreeVo> children) {}
