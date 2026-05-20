package com.flight.query.service.agent.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class BusinessKnowledgeTool {

    private final EmbeddingModel embeddingModel;
    private final InMemoryEmbeddingStore<TextSegment> knowledgeStore;

    public BusinessKnowledgeTool(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
        this.knowledgeStore = new InMemoryEmbeddingStore<>();
    }

    @PostConstruct
    public void init() {
        log.info("[BusinessKnowledgeTool] 开始加载业务知识库...");
        long start = System.currentTimeMillis();

        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:knowledge/*.txt");

            int totalChunks = 0;
            for (Resource resource : resources) {
                List<String> chunks = loadAndSplit(resource);
                for (String chunk : chunks) {
                    TextSegment segment = TextSegment.from(chunk);
                    Embedding embedding = embeddingModel.embed(segment).content();
                    knowledgeStore.add(embedding, segment);
                    totalChunks++;
                }
                log.info("[BusinessKnowledgeTool] 已加载: {}, 分段数: {}",
                        resource.getFilename(), chunks.size());
            }

            long cost = System.currentTimeMillis() - start;
            log.info("[BusinessKnowledgeTool] 知识库加载完成, 共{}个知识片段, 耗时{}ms",
                    totalChunks, cost);

        } catch (Exception e) {
            log.error("[BusinessKnowledgeTool] 知识库加载失败", e);
        }
    }

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

        EmbeddingSearchResult<TextSegment> result = knowledgeStore.search(request);
        List<EmbeddingMatch<TextSegment>> matches = result.matches();

        if (matches.isEmpty()) {
            return "未找到相关的业务知识。";
        }

        StringBuilder sb = new StringBuilder("相关业务知识：\n\n");
        for (int i = 0; i < matches.size(); i++) {
            sb.append(matches.get(i).embedded().text());
            if (i < matches.size() - 1) {
                sb.append("\n\n");
            }
        }

        return sb.toString();
    }

    private List<String> loadAndSplit(Resource resource) {
        List<String> chunks = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {

            String content = reader.lines().collect(Collectors.joining("\n"));
            String[] sections = content.split("(?=【)");

            for (String section : sections) {
                String trimmed = section.trim();
                if (!trimmed.isEmpty()) {
                    chunks.add(trimmed);
                }
            }
        } catch (Exception e) {
            log.error("[BusinessKnowledgeTool] 文件读取失败: {}", resource.getFilename(), e);
        }
        return chunks;
    }
}
