package com.flight.query.service.query;

import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 查询结果封装
 * <p>
 * 三层输出：
 * 1. conclusion - 自然语言分析结论
 * 2. data - 结构化数据表格
 * 3. sql - 生成的SQL（透明可审计）
 */
@Data
public class QueryResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 是否查询成功 */
    private boolean success;

    /** 自然语言分析结论 */
    private String conclusion;

    /** 查询结果数据（表格形式） */
    private List<Map<String, Object>> data;

    /** 生成的SQL（可审计） */
    private String sql;

    /**
     * 成功结果
     */
    public static QueryResult success(String conclusion, List<Map<String, Object>> data, String sql) {
        QueryResult result = new QueryResult();
        result.setSuccess(true);
        result.setConclusion(conclusion);
        result.setData(data);
        result.setSql(sql);
        return result;
    }

    /**
     * 失败结果
     */
    public static QueryResult failed(String message) {
        QueryResult result = new QueryResult();
        result.setSuccess(false);
        result.setConclusion(message);
        return result;
    }
}
