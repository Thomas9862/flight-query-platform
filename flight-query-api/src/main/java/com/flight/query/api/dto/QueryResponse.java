package com.flight.query.api.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 查询响应DTO
 * <p>
 * 三层输出：
 * 1. conclusion - 自然语言分析结论（运营看这个）
 * 2. data - 结构化数据表格（数据分析师看这个）
 * 3. sql - 生成的SQL（开发/审计看这个）
 */
@Data
public class QueryResponse {

    /** 自然语言分析结论 */
    private String conclusion;

    /** 查询结果数据表格 */
    private List<Map<String, Object>> data;

    /** 生成的SQL（可审计） */
    private String sql;

    /** 结果行数 */
    private int rowCount;
}
