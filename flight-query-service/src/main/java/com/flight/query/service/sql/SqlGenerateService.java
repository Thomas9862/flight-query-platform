package com.flight.query.service.sql;

import com.flight.query.common.exception.BusinessException;
import com.flight.query.common.exception.SqlSafetyException;
import com.flight.query.common.result.ErrorCode;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * SQL生成与执行服务
 * <p>
 * 核心流程：
 * 1. 构建完整Prompt（System约束 + Schema + 上下文 + 用户问题）
 * 2. 调通义千问生成SQL
 * 3. SQL安全校验
 * 4. 执行SQL
 * 5. 失败时将报错回传模型修正（while循环，最多3次）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SqlGenerateService {

    private final ChatLanguageModel chatModel;
    private final SqlSafetyChecker safetyChecker;
    private final JdbcTemplate jdbcTemplate;

    /** System Prompt - 四层约束 */
    private static final String SYSTEM_PROMPT =
            "你是一个专业的机票业务数据分析师，精通MySQL SQL语法。\n\n"
            + "【角色定义】\n"
            + "你只负责根据用户的自然语言问题，生成查询 report_reservation_real_time 表的SQL语句。\n\n"
            + "【行为约束】\n"
            + "1. 只生成 SELECT 语句，严禁生成 DELETE、UPDATE、INSERT、DROP 等任何修改数据的操作\n"
            + "2. 不要编造不存在的字段名，只使用下方提供的字段\n"
            + "3. 金额相关分析统一使用 _usd 结尾的字段，保证币种一致\n"
            + "4. 统计真实订单时加条件：virtual_order = 0 OR virtual_order IS NULL\n"
            + "5. 时间筛选统一用 date_value 字段\n\n"
            + "【输出格式约束】\n"
            + "只返回纯SQL语句，不要任何解释、注释或Markdown代码块标记。\n\n"
            + "【边界约束】\n"
            + "如果用户问题与机票订单数据无关，返回：UNSUPPORTED_QUERY";

    /**
     * 生成并执行SQL（含自动修正循环）
     *
     * @param ctx 执行上下文（包含用户问题、Schema、对话上下文）
     * @return 执行上下文（包含结果或错误信息）
     */
    public SqlExecuteContext generateAndExecute(SqlExecuteContext ctx) {
        while (ctx.canRetry()) {
            try {
                // 1. 生成SQL
                String sql = generateSql(ctx);
                ctx.setCurrentSql(sql);

                // 检查是否不支持的查询
                if ("UNSUPPORTED_QUERY".equals(sql.trim())) {
                    throw new BusinessException(ErrorCode.SCHEMA_MATCH_FAILED,
                            "该问题与机票订单数据无关，无法生成查询");
                }

                // 2. 安全校验（不可重试，直接终止）
                safetyChecker.validate(sql);

                // 3. 执行SQL
                List<Map<String, Object>> result = jdbcTemplate.queryForList(sql);
                ctx.setResult(result);
                ctx.setSuccess(true);

                log.info("[SqlGenerateService] SQL执行成功, retryCount={}, sql={}", ctx.getRetryCount(), sql);
                break;

            } catch (SqlSafetyException e) {
                // 安全校验失败，不可重试，直接抛出
                log.error("[SqlGenerateService] SQL安全校验失败: {}", e.getMessage());
                throw e;

            } catch (BusinessException e) {
                // 业务异常，不可重试
                log.warn("[SqlGenerateService] 业务异常: {}", e.getMessage());
                throw e;

            } catch (BadSqlGrammarException e) {
                // SQL语法错误，可重试
                String errorMsg = extractErrorMessage(e);
                ctx.recordError(errorMsg);
                log.warn("[SqlGenerateService] SQL执行失败（语法错误），第{}次重试, error={}",
                        ctx.getRetryCount(), errorMsg);

            } catch (Exception e) {
                // 其他数据库错误，可重试
                String errorMsg = extractErrorMessage(e);
                ctx.recordError(errorMsg);
                log.warn("[SqlGenerateService] SQL执行失败（其他错误），第{}次重试, error={}",
                        ctx.getRetryCount(), errorMsg);
            }
        }

        // 超过最大重试次数
        if (!ctx.isSuccess()) {
            log.error("[SqlGenerateService] SQL生成失败，已达最大重试次数, question={}", ctx.getUserQuestion());
        }

        return ctx;
    }

    /**
     * 调用通义千问生成SQL
     */
    private String generateSql(SqlExecuteContext ctx) {
        String prompt = buildFullPrompt(ctx);

        log.debug("[SqlGenerateService] 生成SQL, prompt长度={}", prompt.length());

        try {
            String response = chatModel.generate(prompt);
            String sql = cleanSqlResponse(response);

            log.info("[SqlGenerateService] 模型返回SQL: {}", sql);
            return sql;

        } catch (Exception e) {
            log.error("[SqlGenerateService] 调用通义千问失败", e);
            throw new BusinessException(ErrorCode.MODEL_CALL_FAILED, "模型调用失败: " + e.getMessage());
        }
    }

    /**
     * 构建完整Prompt
     */
    private String buildFullPrompt(SqlExecuteContext ctx) {
        StringBuilder prompt = new StringBuilder();

        // System角色约束
        prompt.append(SYSTEM_PROMPT).append("\n\n");

        // Schema上下文（动态筛选的字段组）
        if (ctx.getSchemaContext() != null && !ctx.getSchemaContext().isEmpty()) {
            prompt.append("【数据库表结构说明】\n");
            prompt.append(ctx.getSchemaContext()).append("\n");
        }

        // 对话上下文（历史+实体）
        if (ctx.getConversationContext() != null && !ctx.getConversationContext().isEmpty()) {
            prompt.append(ctx.getConversationContext()).append("\n");
        }

        // 用户问题
        prompt.append("【用户问题】\n");
        prompt.append(ctx.getUserQuestion()).append("\n");

        // 修正提示（如果有历史错误）
        if (ctx.hasErrors()) {
            prompt.append(ctx.buildRetryHint());
        }

        prompt.append("\nSQL:");
        return prompt.toString();
    }

    /**
     * 清理模型返回的SQL（去掉Markdown代码块标记等）
     */
    private String cleanSqlResponse(String response) {
        if (response == null || response.isEmpty()) {
            throw new BusinessException(ErrorCode.SQL_GENERATE_FAILED, "模型返回空结果");
        }

        String cleaned = response.trim();

        // 去掉Markdown代码块标记
        if (cleaned.startsWith("```sql")) {
            cleaned = cleaned.substring(6);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }

        // 去掉末尾分号
        cleaned = cleaned.trim();
        if (cleaned.endsWith(";")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
        }

        return cleaned;
    }

    /**
     * 提取异常核心信息（给模型看的，不需要完整堆栈）
     */
    private String extractErrorMessage(Exception e) {
        String msg = e.getMessage();
        if (msg == null) {
            return e.getClass().getSimpleName();
        }
        // 截取前200个字符，避免Prompt过长
        return msg.length() > 200 ? msg.substring(0, 200) : msg;
    }
}
