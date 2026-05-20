package com.flight.query.service.agent.tool;

import com.flight.query.service.schema.FieldGroup;
import com.flight.query.service.schema.SchemaService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SchemaLookupTool {

    private final SchemaService schemaService;

    @Tool("根据用户问题查询相关的数据库表字段信息。返回匹配的字段组及其详细字段说明，包括字段名、类型和含义。在生成SQL之前必须先调用此工具了解可用字段。")
    public String lookupSchema(
            @P("用户的查询问题或关键词，如'利润''航线''支付'") String question) {

        log.info("[SchemaLookupTool] 查询Schema, question={}", question);

        List<FieldGroup> groups = schemaService.matchGroups(question);
        if (groups.isEmpty()) {
            return "未找到与该问题相关的字段组，请尝试换一种描述方式。";
        }

        String schema = schemaService.buildSchemaContext(groups);
        log.info("[SchemaLookupTool] 匹配到{}个字段组: {}",
                groups.size(), schemaService.getMatchedGroupNames(groups));

        return schema;
    }
}
