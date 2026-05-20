package com.flight.query.service.context;

import com.flight.query.common.constant.RedisKeyConstant;
import com.flight.query.common.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 上下文管理服务
 * <p>
 * 两层并行存储设计，解决大模型无状态问题：
 * <p>
 * 第一层：对话历史（Redis List）
 * - 以 sessionId 为 Key，按顺序存储 user/assistant 消息
 * - 保留最近 10 轮对话，超出自动裁剪最早的消息
 * - 30 分钟无操作自动过期
 * <p>
 * 第二层：业务实体（Redis String）
 * - 每轮对话结束后，显式提取航司、时间、地区等关键实体
 * - 新值覆盖旧值，null 不覆盖（保留上下文）
 * - 实体更新通过分布式锁保障串行，防止快速连续提问导致状态覆盖
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContextService {

    private final StringRedisTemplate redisTemplate;

    // ══ 第一层：对话历史 ════════════════════════════════════════

    /**
     * 添加一条用户消息到对话历史
     */
    public void addUserMessage(String sessionId, String message) {
        addMessage(sessionId, "user: " + message);
    }

    /**
     * 添加一条助手回复到对话历史
     */
    public void addAssistantMessage(String sessionId, String message) {
        addMessage(sessionId, "assistant: " + message);
    }

    /**
     * 获取对话历史（用于注入Prompt）
     */
    public List<String> getChatHistory(String sessionId) {
        String key = RedisKeyConstant.chatHistoryKey(sessionId);
        List<String> history = redisTemplate.opsForList().range(key, 0, -1);
        return history != null ? history : Collections.emptyList();
    }

    /**
     * 将对话历史格式化为Prompt文本
     */
    public String buildHistoryPrompt(String sessionId) {
        List<String> history = getChatHistory(sessionId);
        if (history.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n【历史对话记录】\n");
        for (String msg : history) {
            sb.append(msg).append("\n");
        }
        String toString = sb.toString();
        return toString;
    }

    private void addMessage(String sessionId, String message) {
        String key = RedisKeyConstant.chatHistoryKey(sessionId);

        // 追加消息
        redisTemplate.opsForList().rightPush(key, message);

        // 裁剪，只保留最近 MAX_HISTORY_SIZE 条
        Long size = redisTemplate.opsForList().size(key);
        if (size != null && size > RedisKeyConstant.MAX_HISTORY_SIZE) {
            redisTemplate.opsForList().trim(key, size - RedisKeyConstant.MAX_HISTORY_SIZE, -1);
        }

        // 刷新过期时间
        redisTemplate.expire(key, RedisKeyConstant.CHAT_HISTORY_TTL_SECONDS, TimeUnit.SECONDS);
    }

    // ══ 第二层：业务实体 ════════════════════════════════════════

    /**
     * 获取当前会话的业务实体
     */
    public QueryEntity getEntity(String sessionId) {
        String key = RedisKeyConstant.chatEntityKey(sessionId);
        String json = redisTemplate.opsForValue().get(key);
        if (json == null || json.isEmpty()) {
            return new QueryEntity();
        }
        try {
            return JsonUtil.fromJson(json, QueryEntity.class);
        } catch (Exception e) {
            log.warn("[ContextService] 解析实体JSON失败, sessionId={}", sessionId, e);
            return new QueryEntity();
        }
    }

    /**
     * 更新业务实体（加分布式锁，保障同一会话内串行）
     *
     * @param sessionId 会话ID
     * @param newEntity 新提取的实体
     */
    public void updateEntity(String sessionId, QueryEntity newEntity) {
        if (newEntity == null || !newEntity.hasAnyField()) {
            return;
        }

        String lockKey = RedisKeyConstant.entityLockKey(sessionId);
        String entityKey = RedisKeyConstant.chatEntityKey(sessionId);

        // 尝试加锁
        Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", RedisKeyConstant.ENTITY_LOCK_TTL_SECONDS, TimeUnit.SECONDS);

        if (Boolean.TRUE.equals(locked)) {
            try {
                // 读取旧实体 → 合并 → 写回
                QueryEntity existing = getEntity(sessionId);
                existing.merge(newEntity);

                redisTemplate.opsForValue().set(
                        entityKey,
                        JsonUtil.toJson(existing),
                        RedisKeyConstant.CHAT_ENTITY_TTL_SECONDS,
                        TimeUnit.SECONDS
                );

                log.info("[ContextService] 实体更新成功, sessionId={}, entity={}", sessionId, existing);
            } finally {
                // 释放锁
                redisTemplate.delete(lockKey);
            }
        } else {
            // 加锁失败，说明有并发更新，跳过本次更新（不阻塞主流程）
            log.warn("[ContextService] 实体更新加锁失败（并发），跳过, sessionId={}", sessionId);
        }
    }

    /**
     * 将业务实体格式化为Prompt注入文本
     */
    public String buildEntityPrompt(String sessionId) {
        QueryEntity entity = getEntity(sessionId);
        return entity.toPromptText();
    }

    // ══ 清理 ════════════════════════════════════════════════════

    /**
     * 清除会话的全部上下文（历史 + 实体）
     */
    public void clearSession(String sessionId) {
        redisTemplate.delete(RedisKeyConstant.chatHistoryKey(sessionId));
        redisTemplate.delete(RedisKeyConstant.chatEntityKey(sessionId));
        log.info("[ContextService] 会话上下文已清除, sessionId={}", sessionId);
    }
}
