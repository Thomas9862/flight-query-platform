-- ============================================================
-- 机票订单自然语言查询平台 - 初始化SQL
-- ============================================================

-- 查询任务记录表
CREATE TABLE IF NOT EXISTS `query_task` (
    `id`              BIGINT(20)    NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `session_id`      VARCHAR(128)  NOT NULL                COMMENT '会话ID（userId_终端UUID）',
    `user_question`   VARCHAR(500)  NOT NULL                COMMENT '用户原始问题',
    `generated_sql`   TEXT                                  COMMENT '模型生成的SQL',
    `result_json`     MEDIUMTEXT                            COMMENT '查询结果JSON',
    `conclusion`      TEXT                                  COMMENT '自然语言分析结论',
    `status`          TINYINT(4)    NOT NULL DEFAULT 0      COMMENT '状态：0=处理中 1=成功 2=SQL生成失败 3=SQL执行失败 4=安全拦截',
    `retry_count`     INT(11)       NOT NULL DEFAULT 0      COMMENT 'SQL生成重试次数',
    `matched_groups`  VARCHAR(256)                          COMMENT '匹配的字段组名称（逗号分隔）',
    `cost_ms`         BIGINT(20)                            COMMENT '耗时（毫秒）',
    `error_msg`       VARCHAR(1000)                         COMMENT '错误信息',
    `create_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_session_id` (`session_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='自然语言查询任务记录表';
