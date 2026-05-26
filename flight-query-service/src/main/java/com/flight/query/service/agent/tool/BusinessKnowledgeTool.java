package com.flight.query.service.agent.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * RAG 业务知识库检索工具
 * <p>
 * 知识源：MySQL（knowledge_base 表）→ 向量化 → Elasticsearch
 * 检索：bge-small-zh 向量化查询 → ES 余弦相似度匹配 → 返回 top-3 知识片段
 * <p>
 * 知识同步由 {@link com.flight.query.service.knowledge.KnowledgeService} 负责，
 * 本工具只做检索，保持职责单一。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BusinessKnowledgeTool {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    @Tool("查询机票行业业务知识库。包含退改签规则、航司政策、业务指标定义、常见分析方法等专业知识。当用户询问业务概念或需要领域知识辅助分析时使用此工具。")
    public String queryKnowledge(
            @P("要查询的业务问题或关键词") String question) {

        log.info("[BusinessKnowledgeTool] 查询知识库: {}", question);

        Embedding queryEmbedding = embeddingModel.embed(question).content();

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(3)
                .minScore(0.5)
                .build();

        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(request);
        List<EmbeddingMatch<TextSegment>> matches = result.matches();

        if (matches.isEmpty()) {
            return "未找到相关的业务知识。";
        }

        StringBuilder sb = new StringBuilder("相关业务知识：\n\n");
        for (int i = 0; i < matches.size(); i++) {
            EmbeddingMatch<TextSegment> match = matches.get(i);
            sb.append(match.embedded().text());
            sb.append(String.format(" (相似度: %.2f)", match.score()));
            if (i < matches.size() - 1) {
                sb.append("\n\n");
            }
        }

        log.info("[BusinessKnowledgeTool] 检索到{}条相关知识", matches.size());
        return sb.toString();
    }
}
