package com.flight.query.service.schema;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 字段分组定义
 * <p>
 * 将宽表100+字段按业务维度预先分组，每组包含语义化描述和详细字段说明。
 * 服务启动时通过 bge-small-zh 向量化描述文本，存入 InMemoryEmbeddingStore，
 * 查询时计算余弦相似度动态筛选最相关的组注入Prompt。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FieldGroup {

    /** 分组名称（如"利润组"、"航线组"） */
    private String groupName;

    /** 语义化描述（用于向量化匹配，中文） */
    private String semanticDescription;

    /** 详细字段说明（注入到Prompt中的内容） */
    private String fieldDetail;
}
