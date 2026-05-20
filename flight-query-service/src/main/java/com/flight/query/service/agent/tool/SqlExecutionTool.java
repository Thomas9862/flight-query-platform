package com.flight.query.service.agent.tool;

import com.flight.query.common.exception.SqlSafetyException;
import com.flight.query.common.util.JsonUtil;
import com.flight.query.service.sql.SqlSafetyChecker;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SqlExecutionTool {

    private final SqlSafetyChecker safetyChecker;
    private final JdbcTemplate jdbcTemplate;

    @Tool("对 report_reservation_real_time 表执行只读SQL查询。输入必须是有效的SELECT语句。如果SQL执行失败，会返回错误信息，你可以根据错误修正SQL后重新调用此工具。")
    public String executeSql(
            @P("要执行的SQL SELECT语句") String sql) {

        log.info("[SqlExecutionTool] 执行SQL: {}", sql);

        try {
            safetyChecker.validate(sql);

            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);

            if (results.isEmpty()) {
                return "查询结果为空，没有匹配的数据。请检查筛选条件是否过于严格。";
            }

            String json = JsonUtil.toJson(results);
            if (json.length() > 4000) {
                json = json.substring(0, 4000) + "... (共" + results.size() + "条记录，数据已截断)";
            }

            log.info("[SqlExecutionTool] SQL执行成功, 返回{}条记录", results.size());
            return "查询成功，共" + results.size() + "条记录：\n" + json;

        } catch (SqlSafetyException e) {
            log.warn("[SqlExecutionTool] SQL安全校验失败: {}", e.getMessage());
            return "SQL安全校验失败：" + e.getMessage() + "。只允许SELECT查询，禁止任何修改操作。";

        } catch (BadSqlGrammarException e) {
            String msg = extractMessage(e);
            log.warn("[SqlExecutionTool] SQL语法错误: {}", msg);
            return "SQL语法错误：" + msg + "。请检查字段名和SQL语法后重试。";

        } catch (Exception e) {
            String msg = extractMessage(e);
            log.warn("[SqlExecutionTool] SQL执行失败: {}", msg);
            return "SQL执行失败：" + msg + "。请检查字段名和语法后重试。";
        }
    }

    private String extractMessage(Exception e) {
        String msg = e.getMessage();
        if (msg == null) {
            return e.getClass().getSimpleName();
        }
        return msg.length() > 200 ? msg.substring(0, 200) : msg;
    }
}
