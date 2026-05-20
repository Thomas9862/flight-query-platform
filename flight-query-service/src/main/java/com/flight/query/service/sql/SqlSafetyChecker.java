package com.flight.query.service.sql;

import com.flight.query.common.exception.SqlSafetyException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * SQL安全校验器
 * <p>
 * 双层安全防护的代码层：
 * 第一层是 System Prompt 约束模型只生成 SELECT（君子协定）
 * 第二层是本校验器做硬校验（真正的保障），不依赖模型遵守约定
 * 第三层是数据库账号只读权限（最终兜底）
 * <p>
 * 该异常不可重试，一旦触发直接终止查询流程。
 */
@Slf4j
@Component
public class SqlSafetyChecker {

    /** 黑名单关键词（大写匹配） */
    private static final List<String> BLACKLIST_KEYWORDS = Arrays.asList(
            "DROP", "DELETE", "UPDATE", "INSERT", "TRUNCATE", "ALTER",
            "CREATE", "REPLACE", "RENAME", "GRANT", "REVOKE",
            "EXEC", "EXECUTE", "CALL",
            "LOAD", "INTO OUTFILE", "INTO DUMPFILE",
            "SHUTDOWN", "KILL"
    );

    /** 检测SQL注释注入 */
    private static final Pattern COMMENT_PATTERN = Pattern.compile("(--|#|/\\*)", Pattern.CASE_INSENSITIVE);

    /** 检测多语句（分号分隔） */
    private static final Pattern MULTI_STATEMENT_PATTERN = Pattern.compile(";\\s*\\S");

    /**
     * 校验SQL安全性
     *
     * @param sql 待校验的SQL
     * @throws SqlSafetyException 包含非法操作时抛出
     */
    public void validate(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            throw new SqlSafetyException("SQL不能为空");
        }

        String trimmedSql = sql.trim();
        String upperSql = trimmedSql.toUpperCase();

        // 1. 必须以 SELECT 开头
        if (!upperSql.startsWith("SELECT")) {
            log.error("[SqlSafetyChecker] SQL不是SELECT语句: {}", sql);
            throw new SqlSafetyException("只允许SELECT查询语句");
        }

        // 2. 黑名单关键词检查
        for (String keyword : BLACKLIST_KEYWORDS) {
            // 用单词边界匹配，避免字段名包含关键词误判（如 update_time 字段）
            String regex = "\\b" + keyword + "\\b";
            if (Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(trimmedSql).find()) {
                // 排除SELECT语句中的字段名，只检查非字段上下文
                if (isInDangerousContext(trimmedSql, keyword)) {
                    log.error("[SqlSafetyChecker] SQL包含非法关键词: {}, sql: {}", keyword, sql);
                    throw new SqlSafetyException("SQL包含非法关键词: " + keyword);
                }
            }
        }

        // 3. 检查SQL注入常见模式
        if (COMMENT_PATTERN.matcher(trimmedSql).find()) {
            log.error("[SqlSafetyChecker] SQL包含注释符号: {}", sql);
            throw new SqlSafetyException("SQL包含非法注释符号");
        }

        // 4. 检查多语句
        if (MULTI_STATEMENT_PATTERN.matcher(trimmedSql).find()) {
            log.error("[SqlSafetyChecker] SQL包含多条语句: {}", sql);
            throw new SqlSafetyException("不允许执行多条SQL语句");
        }

        log.debug("[SqlSafetyChecker] SQL安全校验通过: {}", sql);
    }

    /**
     * 判断关键词是否在危险上下文中（而不是字段名中）
     * <p>
     * 例如 "SELECT update_time FROM xxx" 中 update_time 是字段名，不危险
     * 而 "SELECT 1; DROP TABLE xxx" 中 DROP 是危险操作
     */
    private boolean isInDangerousContext(String sql, String keyword) {
        String upperSql = sql.toUpperCase();
        String upperKeyword = keyword.toUpperCase();

        // 如果关键词出现在 SELECT 和 FROM 之间，大概率是字段名，不拦截
        int selectIdx = upperSql.indexOf("SELECT");
        int fromIdx = upperSql.indexOf("FROM");

        if (selectIdx >= 0 && fromIdx > selectIdx) {
            String selectClause = upperSql.substring(selectIdx, fromIdx);
            String whereClause = fromIdx < upperSql.length() ? upperSql.substring(fromIdx) : "";

            // 如果关键词只在 SELECT 子句中出现（字段名），不拦截
            if (selectClause.contains(upperKeyword) && !whereClause.contains(upperKeyword)) {
                return false;
            }
        }

        // 其他情况都认为是危险的
        return true;
    }
}
