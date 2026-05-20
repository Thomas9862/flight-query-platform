package com.flight.query.common.result;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 业务错误码定义
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {

    SUCCESS(200, "成功"),

    // ── 参数校验 4xx ─────────────────────────────────────────
    PARAM_ERROR(400, "参数错误"),
    SESSION_REQUIRED(401, "缺少会话标识"),
    QUESTION_EMPTY(402, "查询问题不能为空"),
    QUESTION_TOO_LONG(403, "查询问题过长，请精简后重试"),

    // ── 业务错误 5xx ─────────────────────────────────────────
    SYSTEM_ERROR(500, "系统内部错误"),
    SQL_GENERATE_FAILED(510, "SQL生成失败，请换一种描述方式"),
    SQL_EXECUTE_FAILED(511, "SQL执行失败"),
    SQL_SAFETY_VIOLATION(512, "SQL包含非法操作"),
    SCHEMA_MATCH_FAILED(513, "无法理解查询意图，请换一种描述方式"),
    MODEL_CALL_FAILED(520, "模型调用失败，请稍后重试"),
    CONTEXT_UPDATE_FAILED(530, "上下文更新失败"),

    // ── 限流相关 6xx ─────────────────────────────────────────
    RATE_LIMITED(601, "查询过于频繁，请稍后重试"),
    DUPLICATE_QUERY(602, "相同查询正在处理中，请勿重复提交");

    private final int code;
    private final String message;
}
