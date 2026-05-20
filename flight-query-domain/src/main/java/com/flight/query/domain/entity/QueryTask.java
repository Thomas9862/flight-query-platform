package com.flight.query.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 查询任务记录表
 * <p>
 * 记录每次自然语言查询的完整生命周期：
 * 用户问题 → 生成的SQL → 查询结果 → 自然语言结论 → 状态
 */
@Data
@TableName("query_task")
public class QueryTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会话ID（userId + 终端UUID） */
    private String sessionId;

    /** 用户原始问题 */
    private String userQuestion;

    /** 模型生成的SQL */
    private String generatedSql;

    /** 查询结果JSON */
    private String resultJson;

    /** 自然语言分析结论 */
    private String conclusion;

    /**
     * 查询状态
     * 0=处理中 1=成功 2=SQL生成失败 3=SQL执行失败 4=安全拦截
     */
    private Integer status;

    /** SQL生成重试次数 */
    private Integer retryCount;

    /** 匹配的字段组名称（逗号分隔） */
    private String matchedGroups;

    /** 耗时（毫秒） */
    private Long costMs;

    /** 错误信息 */
    private String errorMsg;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    // ── 状态常量 ──────────────────────────────────────────────

    public static final int STATUS_PROCESSING = 0;
    public static final int STATUS_SUCCESS = 1;
    public static final int STATUS_SQL_GENERATE_FAILED = 2;
    public static final int STATUS_SQL_EXECUTE_FAILED = 3;
    public static final int STATUS_SAFETY_BLOCKED = 4;
}
