package com.flight.query.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * RAG业务知识库实体
 * <p>
 * MySQL 存储知识原文，配合 Elasticsearch 做向量检索。
 * 分类：BUSINESS_GLOSSARY / FARE_RULES / AIRLINE_KNOWLEDGE / QUERY_GUIDE
 */
@Data
@TableName("knowledge_base")
public class KnowledgeBase {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 知识分类 */
    private String category;

    /** 知识标题 */
    private String title;

    /** 知识内容 */
    private String content;

    /** 状态：1=启用 0=禁用 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    // ── 状态常量 ──────────────────────────────────────────────
    public static final int STATUS_ENABLED = 1;
    public static final int STATUS_DISABLED = 0;

    // ── 分类常量 ──────────────────────────────────────────────
    public static final String CATEGORY_BUSINESS_GLOSSARY = "BUSINESS_GLOSSARY";
    public static final String CATEGORY_FARE_RULES = "FARE_RULES";
    public static final String CATEGORY_AIRLINE_KNOWLEDGE = "AIRLINE_KNOWLEDGE";
    public static final String CATEGORY_QUERY_GUIDE = "QUERY_GUIDE";
}
