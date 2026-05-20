package com.flight.query.api.controller;

import com.flight.query.api.dto.QueryRequest;
import com.flight.query.common.result.Result;
import com.flight.query.service.agent.AgentQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@Slf4j
@RestController
@RequestMapping("/api/query")
@RequiredArgsConstructor
public class QueryController {

    private final AgentQueryService agentQueryService;

    @PostMapping
    public Result<String> query(@RequestBody QueryRequest request) {
        log.info("[QueryController] 收到查询请求, sessionId={}, question={}",
                request.getSessionId(), request.getQuestion());

        String answer = agentQueryService.query(
                request.getSessionId(), request.getQuestion());

        return Result.success(answer);
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> queryStream(@RequestBody QueryRequest request) {
        log.info("[QueryController] 收到流式查询请求, sessionId={}, question={}",
                request.getSessionId(), request.getQuestion());

        return agentQueryService.queryStream(
                        request.getSessionId(), request.getQuestion())
                .map(token -> ServerSentEvent.<String>builder()
                        .data(token)
                        .build())
                .concatWith(Flux.just(ServerSentEvent.<String>builder()
                        .event("done")
                        .data("[DONE]")
                        .build()));
    }

    @GetMapping("/health")
    public Result<String> health() {
        return Result.success("Flight Query Platform is running");
    }
}
