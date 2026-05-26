package com.flight.query.api.dto;

import lombok.Data;

/**
 * Schema 字段组操作请求 DTO
 */
@Data
public class SchemaFieldGroupRequest {

    /** 所属表名（不传则默认 report_reservation_real_time） */
    private String tableName;

    /** 字段组名称 */
    private String groupName;

    /** 中文语义描述（用于向量匹配） */
    private String semanticDesc;

    /** 字段详细说明（匹配后注入 Prompt） */
    private String fieldDetail;

    /** 状态：1=启用 0=禁用 */
    private Integer status;

    /** 排序序号 */
    private Integer sortOrder;
}
