package com.flight.query.service.query;

import com.flight.query.common.constant.RedisKeyConstant;
import com.flight.query.common.exception.BusinessException;
import com.flight.query.common.result.ErrorCode;
import com.flight.query.common.util.JsonUtil;
import com.flight.query.domain.entity.QueryTask;
import com.flight.query.domain.mapper.QueryTaskMapper;
import com.flight.query.service.context.ContextService;
import com.flight.query.service.context.QueryEntity;
import com.flight.query.service.schema.FieldGroup;
import com.flight.query.service.schema.SchemaService;
import com.flight.query.service.sql.SqlExecuteContext;
import com.flight.query.service.sql.SqlGenerateService;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 查询主流程编排服务
 * <p>
 * 串联所有组件，实现完整的查询流程：
 * 1. 防重校验
 * 2. 语义缓存命中检查
 * 3. 读取会话上下文（对话历史 + 业务实体）
 * 4. Schema向量匹配，筛选相关字段组
 * 5. 调模型生成SQL + 自动修正循环
 * 6. 调模型二次解释结果
 * 7. 更新对话历史和业务实体
 * 8. 缓存结果
 * 9. 记录查询任务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueryService {

    private final SchemaService schemaService;
    private final ContextService contextService;
    private final SqlGenerateService sqlGenerateService;
    private final ChatLanguageModel chatModel;
    private final StringRedisTemplate redisTemplate;
    private final QueryTaskMapper queryTaskMapper;

    /**
     * 执行自然语言查询（主入口）
     *
     * @param sessionId    会话ID（userId_终端UUID）
     * @param userQuestion 用户原始问题
     * @return 查询结果
     */
    public QueryResult query(String sessionId, String userQuestion) {
        long startTime = System.currentTimeMillis();

        // 0. 参数校验
        validateInput(sessionId, userQuestion);

        // 1. 防重校验
        checkDuplicate(sessionId, userQuestion);

        // 2. 语义缓存命中检查
        QueryResult cached = checkCache(userQuestion);
        if (cached != null) {
            log.info("[QueryService] 命中语义缓存, question={}", userQuestion);
            return cached;
        }

        // 3. 读取会话上下文
        String historyPrompt = contextService.buildHistoryPrompt(sessionId);
        String entityPrompt = contextService.buildEntityPrompt(sessionId);
        String conversationContext = historyPrompt + entityPrompt;

        // 4. Schema向量匹配
        List<FieldGroup> matchedGroups = schemaService.matchGroups(userQuestion);
        if (matchedGroups.isEmpty()) {
            throw new BusinessException(ErrorCode.SCHEMA_MATCH_FAILED);
        }
        String schemaContext = schemaService.buildSchemaContext(matchedGroups);

        // 5. 构建执行上下文，调模型生成SQL + 自动修正
        SqlExecuteContext ctx = new SqlExecuteContext();
        ctx.setUserQuestion(userQuestion);
        ctx.setSchemaContext(schemaContext);
        ctx.setConversationContext(conversationContext);

        SqlExecuteContext executedCtx = sqlGenerateService.generateAndExecute(ctx);

        // 6. 判断执行结果
        QueryResult queryResult;
        if (executedCtx.isSuccess()) {
            // 6a. 成功：调模型二次解释结果
            String conclusion = interpretResult(userQuestion, executedCtx.getCurrentSql(), executedCtx.getResult());

            queryResult = QueryResult.success(
                    conclusion,
                    executedCtx.getResult(),
                    executedCtx.getCurrentSql()
            );

            // 7. 更新对话历史和业务实体
            updateContext(sessionId, userQuestion, conclusion);

            // 8. 缓存结果
            cacheResult(userQuestion, queryResult);

        } else {
            // 6b. 失败
            queryResult = QueryResult.failed("无法生成有效的查询语句，请换一种描述方式");
        }

        // 9. 记录查询任务
        long costMs = System.currentTimeMillis() - startTime;
        saveQueryTask(sessionId, userQuestion, executedCtx, queryResult, matchedGroups, costMs);

        log.info("[QueryService] 查询完成, sessionId={}, costMs={}, success={}",
                sessionId, costMs, queryResult.isSuccess());

        return queryResult;
    }

    // ── 参数校验 ──────────────────────────────────────────────

    private void validateInput(String sessionId, String userQuestion) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.SESSION_REQUIRED);
        }
        if (userQuestion == null || userQuestion.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.QUESTION_EMPTY);
        }
        if (userQuestion.length() > 500) {
            throw new BusinessException(ErrorCode.QUESTION_TOO_LONG);
        }
    }

    // ── 防重校验 ──────────────────────────────────────────────

    private void checkDuplicate(String sessionId, String userQuestion) {
        String questionHash = String.valueOf(userQuestion.hashCode());
        String lockKey = RedisKeyConstant.duplicateLockKey(sessionId, questionHash);

        Boolean absent = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", RedisKeyConstant.DUPLICATE_LOCK_TTL_SECONDS, TimeUnit.SECONDS);

        if (Boolean.FALSE.equals(absent)) {
            throw new BusinessException(ErrorCode.DUPLICATE_QUERY);
        }
    }

    // ── 语义缓存 ──────────────────────────────────────────────

    private QueryResult checkCache(String userQuestion) {
        // 语义归一化：去除多余空格、统一标点
        String normalized = normalizeQuestion(userQuestion);
        String cacheHash = String.valueOf(normalized.hashCode());
        String cacheKey = RedisKeyConstant.queryCacheKey(cacheHash);

        String cachedJson = redisTemplate.opsForValue().get(cacheKey);
        if (cachedJson != null && !cachedJson.isEmpty()) {
            return JsonUtil.fromJson(cachedJson, QueryResult.class);
        }
        return null;
    }

    private void cacheResult(String userQuestion, QueryResult result) {
        if (!result.isSuccess()) {
            return; // 只缓存成功的查询
        }
        try {
            String normalized = normalizeQuestion(userQuestion);
            String cacheHash = String.valueOf(normalized.hashCode());
            String cacheKey = RedisKeyConstant.queryCacheKey(cacheHash);

            redisTemplate.opsForValue().set(
                    cacheKey,
                    JsonUtil.toJson(result),
                    RedisKeyConstant.QUERY_CACHE_TTL_SECONDS,
                    TimeUnit.SECONDS
            );
        } catch (Exception e) {
            // 缓存写入失败不影响主流程
            log.warn("[QueryService] 缓存写入失败", e);
        }
    }

    /**
     * 语义归一化：去除多余空格、统一标点、转小写
     */
    private String normalizeQuestion(String question) {
        return question.trim()
                .replaceAll("\\s+", " ")
                .replaceAll("[？?]", "?")
                .replaceAll("[，,]", ",")
                .toLowerCase();
    }

    // ── 结果解释 ──────────────────────────────────────────────

    private String interpretResult(String userQuestion, String sql, List<Map<String, Object>> result) {
        String resultJson = JsonUtil.toJson(result);

        // 限制结果长度，避免Prompt过长
        if (resultJson.length() > 3000) {
            resultJson = resultJson.substring(0, 3000) + "... (数据已截断)";
        }

        String prompt =
                "用户问题：" + userQuestion + "\n"
                + "执行的SQL：" + sql + "\n"
                + "查询结果：" + resultJson + "\n\n"
                + "请用简洁的中文给出分析结论，包括：\n"
                + "1. 直接回答用户的问题\n"
                + "2. 数据中的关键发现（最高/最低/异常值）\n"
                + "3. 如果结果为空，说明可能原因\n\n"
                + "注意：不要重复列出原始数据，直接给出结论。如果涉及具体金额，标注USD单位。";

        try {
            return chatModel.generate(prompt);
        } catch (Exception e) {
            log.warn("[QueryService] 结果解释调用失败，返回默认结论", e);
            return "查询已完成，请查看数据表格获取详细结果。";
        }
    }

    // ── 上下文更新 ───────────────────────────────────────────

    private void updateContext(String sessionId, String userQuestion, String conclusion) {
        try {
            // 更新对话历史
            contextService.addUserMessage(sessionId, userQuestion);
            contextService.addAssistantMessage(sessionId, conclusion);

            // 提取并更新业务实体
            QueryEntity newEntity = extractEntity(userQuestion);
            if (newEntity != null && newEntity.hasAnyField()) {
                contextService.updateEntity(sessionId, newEntity);
            }
        } catch (Exception e) {
            // 上下文更新失败不影响主流程
            log.warn("[QueryService] 上下文更新失败, sessionId={}", sessionId, e);
        }
    }

    /**
     * 调用模型提取业务实体
     */
    private QueryEntity extractEntity(String userQuestion) {
        String prompt =
                "从以下用户问题中提取业务实体，JSON格式返回，没有的字段为null：\n"
                + "{\n"
                + "  \"airline\": \"航司名称或代码\",\n"
                + "  \"dateRange\": \"时间范围描述\",\n"
                + "  \"region\": \"地区/大洲\",\n"
                + "  \"route\": \"航线\",\n"
                + "  \"metric\": \"分析指标\",\n"
                + "  \"brand\": \"品牌\",\n"
                + "  \"market\": \"市场/国家\"\n"
                + "}\n\n"
                + "用户问题：" + userQuestion + "\n"
                + "只返回JSON，不要任何其他内容。";

        try {
            String response = chatModel.generate(prompt);
            String cleanedJson = JsonUtil.cleanModelJson(response);
            return JsonUtil.fromJson(cleanedJson, QueryEntity.class);
        } catch (Exception e) {
            log.warn("[QueryService] 实体提取失败, question={}", userQuestion, e);
            return null;
        }
    }

    // ── 任务记录 ──────────────────────────────────────────────

    private void saveQueryTask(String sessionId, String userQuestion,
                               SqlExecuteContext ctx, QueryResult result,
                               List<FieldGroup> matchedGroups, long costMs) {
        try {
            QueryTask task = new QueryTask();
            task.setSessionId(sessionId);
            task.setUserQuestion(userQuestion);
            task.setGeneratedSql(ctx.getCurrentSql());
            task.setRetryCount(ctx.getRetryCount());
            task.setMatchedGroups(schemaService.getMatchedGroupNames(matchedGroups));
            task.setCostMs(costMs);

            if (result.isSuccess()) {
                task.setStatus(QueryTask.STATUS_SUCCESS);
                task.setConclusion(result.getConclusion());
                // 结果JSON过大时截断，避免占用过多存储
                String resultJson = JsonUtil.toJson(result.getData());
                task.setResultJson(resultJson.length() > 10000
                        ? resultJson.substring(0, 10000) : resultJson);
            } else {
                task.setStatus(ctx.hasErrors()
                        ? QueryTask.STATUS_SQL_EXECUTE_FAILED
                        : QueryTask.STATUS_SQL_GENERATE_FAILED);
                task.setErrorMsg(result.getConclusion());
            }

            queryTaskMapper.insert(task);
        } catch (Exception e) {
            // 任务记录失败不影响主流程
            log.warn("[QueryService] 查询任务记录失败", e);
        }
    }
}
