package com.flight.query.api.controller;

import com.flight.query.api.dto.QueryRequest;
import com.flight.query.api.dto.QueryResponse;
import com.flight.query.common.result.Result;
import com.flight.query.service.context.ContextService;
import com.flight.query.service.query.QueryResult;
import com.flight.query.service.query.QueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 查询接口
 */
@Slf4j
@RestController
@RequestMapping("/api/query")
@RequiredArgsConstructor
public class QueryController {

    private final QueryService queryService;
    private final ContextService contextService;

    /**
     * 自然语言查询
     * <p>
     * 输入自然语言问题，返回三层结果：
     * 1. 自然语言分析结论
     * 2. 结构化数据表格
     * 3. 生成的SQL（可审计）
     */
    @PostMapping
    public Result<QueryResponse> query(@RequestBody QueryRequest request) {
        log.info("[QueryController] 收到查询请求, sessionId={}, question={}",
                request.getSessionId(), request.getQuestion());

        QueryResult queryResult = queryService.query(request.getSessionId(), request.getQuestion());

        if (!queryResult.isSuccess()) {
            return Result.fail(queryResult.getConclusion());
        }

        QueryResponse response = new QueryResponse();
        response.setConclusion(queryResult.getConclusion());
        response.setData(queryResult.getData());
        response.setSql(queryResult.getSql());
        response.setRowCount(queryResult.getData() != null ? queryResult.getData().size() : 0);

        return Result.success(response);
    }

    /**
     * 清除会话上下文
     * <p>
     * 清除对话历史和业务实体，开始全新对话
     */
    @DeleteMapping("/session/{sessionId}")
    public Result<Void> clearSession(@PathVariable String sessionId) {
        log.info("[QueryController] 清除会话上下文, sessionId={}", sessionId);
        contextService.clearSession(sessionId);
        return Result.success();
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public Result<String> health() {
        return Result.success("Flight Query Platform is running");
    }
}
