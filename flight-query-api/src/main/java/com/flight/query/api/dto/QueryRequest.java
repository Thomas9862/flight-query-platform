package com.flight.query.api.dto;

import lombok.Data;

/**
 * 查询请求DTO
 */
@Data
public class QueryRequest {

    /**
     * 会话ID
     * <p>
     * 格式：userId_终端UUID
     * 由前端生成，保障同一用户不同窗口的会话隔离
     */
    private String sessionId;

    /**
     * 用户自然语言问题
     * <p>
     * 示例：上周欧洲航线按航司的利润排名
     */
    private String question;
}
