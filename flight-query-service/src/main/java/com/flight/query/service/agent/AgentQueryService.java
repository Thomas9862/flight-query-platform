package com.flight.query.service.agent;

import com.flight.query.common.constant.RedisKeyConstant;
import com.flight.query.common.exception.BusinessException;
import com.flight.query.common.result.ErrorCode;
import com.flight.query.common.util.JsonUtil;
import com.flight.query.domain.entity.QueryTask;
import com.flight.query.domain.mapper.QueryTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentQueryService {

    private final FlightQueryAgent agent;
    private final StringRedisTemplate redisTemplate;
    private final QueryTaskMapper queryTaskMapper;

    public String query(String sessionId, String question) {
        long startTime = System.currentTimeMillis();

        validateInput(sessionId, question);
        checkDuplicate(sessionId, question);

        String cached = checkCache(question);
        if (cached != null) {
            log.info("[AgentQueryService] 命中语义缓存, question={}", question);
            return cached;
        }

        String answer = agent.chat(sessionId, question);

        cacheResult(question, answer);
        saveQueryTask(sessionId, question, answer, System.currentTimeMillis() - startTime);

        log.info("[AgentQueryService] 查询完成, sessionId={}, costMs={}",
                sessionId, System.currentTimeMillis() - startTime);

        return answer;
    }

    public Flux<String> queryStream(String sessionId, String question) {
        validateInput(sessionId, question);
        checkDuplicate(sessionId, question);

        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();

        long startTime = System.currentTimeMillis();
        StringBuilder fullAnswer = new StringBuilder();

        agent.chatStream(sessionId, question)
                .onPartialResponse(token -> {
                    fullAnswer.append(token);
                    sink.tryEmitNext(token);
                })
                .onComplete(response -> {
                    sink.tryEmitComplete();
                    String answer = fullAnswer.toString();
                    cacheResult(question, answer);
                    saveQueryTask(sessionId, question, answer,
                            System.currentTimeMillis() - startTime);
                })
                .onError(error -> {
                    log.error("[AgentQueryService] 流式查询失败", error);
                    sink.tryEmitError(error);
                })
                .start();

        return sink.asFlux();
    }

    private void validateInput(String sessionId, String question) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.SESSION_REQUIRED);
        }
        if (question == null || question.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.QUESTION_EMPTY);
        }
        if (question.length() > 500) {
            throw new BusinessException(ErrorCode.QUESTION_TOO_LONG);
        }
    }

    private void checkDuplicate(String sessionId, String question) {
        String questionHash = String.valueOf(question.hashCode());
        String lockKey = RedisKeyConstant.duplicateLockKey(sessionId, questionHash);

        Boolean absent = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", RedisKeyConstant.DUPLICATE_LOCK_TTL_SECONDS, TimeUnit.SECONDS);

        if (Boolean.FALSE.equals(absent)) {
            throw new BusinessException(ErrorCode.DUPLICATE_QUERY);
        }
    }

    private String checkCache(String question) {
        String normalized = normalizeQuestion(question);
        String cacheHash = String.valueOf(normalized.hashCode());
        String cacheKey = RedisKeyConstant.queryCacheKey(cacheHash);

        return redisTemplate.opsForValue().get(cacheKey);
    }

    private void cacheResult(String question, String answer) {
        try {
            String normalized = normalizeQuestion(question);
            String cacheHash = String.valueOf(normalized.hashCode());
            String cacheKey = RedisKeyConstant.queryCacheKey(cacheHash);

            redisTemplate.opsForValue().set(
                    cacheKey, answer,
                    RedisKeyConstant.QUERY_CACHE_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("[AgentQueryService] 缓存写入失败", e);
        }
    }

    private String normalizeQuestion(String question) {
        return question.trim()
                .replaceAll("\\s+", " ")
                .replaceAll("[？?]", "?")
                .replaceAll("[，,]", ",")
                .toLowerCase();
    }

    private void saveQueryTask(String sessionId, String question,
                               String answer, long costMs) {
        try {
            QueryTask task = new QueryTask();
            task.setSessionId(sessionId);
            task.setUserQuestion(question);
            task.setCostMs(costMs);
            task.setRetryCount(0);
            task.setStatus(QueryTask.STATUS_SUCCESS);
            task.setConclusion(answer.length() > 2000
                    ? answer.substring(0, 2000) : answer);

            queryTaskMapper.insert(task);
        } catch (Exception e) {
            log.warn("[AgentQueryService] 查询任务记录失败", e);
        }
    }
}
