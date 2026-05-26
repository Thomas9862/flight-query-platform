package com.flight.query.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Schema 字段组实体
 * <p>
 * 存储在 MySQL 中，替代原来硬编码的 FieldGroupRegistry。
 * 启动时加载 → 向量化 → InMemoryEmbeddingStore，Agent 调用时按余弦相似度匹配。
 * table_name 字段预留多表扩展能力。
 */
@Data
@TableName("schema_field_group")
public class SchemaFieldGroup {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属表名（预留多表扩展） */
    private String tableName;

    /** 字段组名称，如"利润组" */
    private String groupName;

    /** 中文语义描述（用于向量匹配） */
    private String semanticDesc;

    /** 字段详细说明（匹配后注入 Prompt） */
    private String fieldDetail;

    /** 状态：1=启用 0=禁用 */
    private Integer status;

    /** 排序序号 */
    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    // ── 状态常量 ──────────────────────────────────────────────
    public static final int STATUS_ENABLED = 1;
    public static final int STATUS_DISABLED = 0;
}
