package com.flight.query.service.sql;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * SQL执行上下文
 * <p>
 * 替代 LangGraph 的 State 概念，在 while 循环中贯穿整个修正过程。
 * 记录每一轮的 SQL 生成历史和报错历史，修正时带给模型参考。
 */
@Data
public class SqlExecuteContext {

    /** 用户原始问题 */
    private String userQuestion;

    /** Schema上下文（动态注入的字段组说明） */
    private String schemaContext;

    /** 对话上下文（历史+实体） */
    private String conversationContext;

    /** 当前生成的SQL */
    private String currentSql;

    /** SQL生成历史（每轮生成的SQL） */
    private List<String> sqlHistory = new ArrayList<>();

    /** 报错历史（每轮执行的错误信息） */
    private List<String> errors = new ArrayList<>();

    /** 查询结果 */
    private List<Map<String, Object>> result;

    /** 是否执行成功 */
    private boolean success = false;

    /** 当前重试次数 */
    private int retryCount = 0;

    /** 最大重试次数 */
    private int maxRetry = 3;

    /**
     * 是否还可以重试
     */
    public boolean canRetry() {
        return !success && retryCount < maxRetry;
    }

    /**
     * 记录一次执行失败
     */
    public void recordError(String errorMessage) {
        if (currentSql != null) {
            sqlHistory.add(currentSql);
        }
        errors.add(errorMessage);
        retryCount++;
    }

    /**
     * 是否有历史错误（用于判断是否需要在Prompt中加入修正信息）
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    /**
     * 构建修正提示（将历史SQL和报错信息拼成Prompt）
     */
    public String buildRetryHint() {
        if (!hasErrors()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n\n【注意】之前生成的SQL执行失败，请根据报错信息修正：\n");

        for (int i = 0; i < errors.size(); i++) {
            sb.append("第").append(i + 1).append("次尝试的SQL：").append(sqlHistory.get(i)).append("\n");
            sb.append("报错信息：").append(errors.get(i)).append("\n\n");
        }

        sb.append("请根据以上报错生成正确的SQL，不要重复之前的错误。\n");
        return sb.toString();
    }
}
