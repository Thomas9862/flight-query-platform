package com.flight.query.api.dto;

import lombok.Data;

/**
 * 知识库操作请求 DTO
 */
@Data
public class KnowledgeRequest {

    /** 知识分类：BUSINESS_GLOSSARY / FARE_RULES / AIRLINE_KNOWLEDGE / QUERY_GUIDE */
    private String category;

    /** 知识标题 */
    private String title;

    /** 知识内容 */
    private String content;

    /** 状态：1=启用 0=禁用 */
    private Integer status;
}
