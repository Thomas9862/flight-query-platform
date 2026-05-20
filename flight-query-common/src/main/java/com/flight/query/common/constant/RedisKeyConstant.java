package com.flight.query.common.constant;

/**
 * Redis Key 常量定义
 * <p>
 * 所有Key均携带SessionId前缀，保障不同用户会话的完全隔离。
 * 模式：{业务前缀}:{sessionId}
 */
public final class RedisKeyConstant {

    private RedisKeyConstant() {
    }

    // ── Key 前缀 ──────────────────────────────────────────────

    /** 对话历史 Key 前缀 */
    public static final String CHAT_HISTORY_PREFIX = "flight:chat:history:";

    /** 业务实体 Key 前缀 */
    public static final String CHAT_ENTITY_PREFIX = "flight:chat:entity:";

    /** 实体更新分布式锁前缀 */
    public static final String ENTITY_LOCK_PREFIX = "flight:lock:entity:";

    /** 查询结果语义缓存前缀 */
    public static final String QUERY_CACHE_PREFIX = "flight:cache:query:";

    /** 防重提交锁前缀 */
    public static final String DUPLICATE_LOCK_PREFIX = "flight:lock:duplicate:";

    // ── 过期时间（秒）────────────────────────────────────────

    /** 对话历史过期时间：30分钟 */
    public static final long CHAT_HISTORY_TTL_SECONDS = 30 * 60;

    /** 业务实体过期时间：30分钟 */
    public static final long CHAT_ENTITY_TTL_SECONDS = 30 * 60;

    /** 实体更新锁过期时间：5秒 */
    public static final long ENTITY_LOCK_TTL_SECONDS = 5;

    /** 查询缓存过期时间：5分钟 */
    public static final long QUERY_CACHE_TTL_SECONDS = 5 * 60;

    /** 防重锁过期时间：10秒 */
    public static final long DUPLICATE_LOCK_TTL_SECONDS = 10;

    // ── 对话历史配置 ──────────────────────────────────────────

    /** 保留最近N轮对话（一问一答算一轮，存2条消息） */
    public static final int MAX_HISTORY_ROUNDS = 10;

    /** 每轮对话2条消息（user + assistant），最大保留条数 */
    public static final int MAX_HISTORY_SIZE = MAX_HISTORY_ROUNDS * 2;

    // ── Key 构建方法 ──────────────────────────────────────────

    public static String chatHistoryKey(String sessionId) {
        return CHAT_HISTORY_PREFIX + sessionId;
    }

    public static String chatEntityKey(String sessionId) {
        return CHAT_ENTITY_PREFIX + sessionId;
    }

    public static String entityLockKey(String sessionId) {
        return ENTITY_LOCK_PREFIX + sessionId;
    }

    public static String queryCacheKey(String cacheHash) {
        return QUERY_CACHE_PREFIX + cacheHash;
    }

    public static String duplicateLockKey(String sessionId, String questionHash) {
        return DUPLICATE_LOCK_PREFIX + sessionId + ":" + questionHash;
    }
}
