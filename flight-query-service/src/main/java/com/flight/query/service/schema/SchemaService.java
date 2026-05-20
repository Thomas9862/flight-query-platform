package com.flight.query.service.schema;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Schema语义匹配服务
 * <p>
 * 核心职责：
 * 1. 服务启动时将所有字段组的语义描述向量化，存入 InMemoryEmbeddingStore
 * 2. 用户问题进来时，向量化后计算余弦相似度，筛选最相关的字段组
 * 3. 将匹配到的字段组的详细说明拼接为Prompt注入内容
 * <p>
 * 向量化使用 LangChain4j 内置的 bge-small-zh（ONNX本地模型），
 * JVM进程内本地运行，无需部署外部服务，毫秒级响应。
 */
@Slf4j
@Service
public class SchemaService {

    /** InMemoryEmbeddingStore - 内存向量存储 */
    private final InMemoryEmbeddingStore<TextSegment> embeddingStore;

    /** bge-small-zh ONNX本地向量化模型 */
    private final EmbeddingModel embeddingModel;

    /** 所有字段组定义 */
    private List<FieldGroup> allGroups;

    /** 相似度阈值，低于此值的字段组不注入（0~1，越高越严格） */
    private static final double SIMILARITY_THRESHOLD = 0.5;

    /** 最大返回字段组数 */
    private static final int MAX_MATCH_COUNT = 3;

    public SchemaService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = new InMemoryEmbeddingStore<>();
    }

    /**
     * 服务启动时初始化：向量化所有字段组描述并存入Store
     */
    @PostConstruct
    public void init() {
        this.allGroups = FieldGroupRegistry.buildAllGroups();

        log.info("[SchemaService] 开始向量化字段组描述，共{}组", allGroups.size());
        long start = System.currentTimeMillis();

        for (FieldGroup group : allGroups) {
            // 用语义描述做向量化（不是字段详情，那个太长且包含技术名词，语义匹配效果差）
            TextSegment segment = TextSegment.from(group.getSemanticDescription());
            Embedding embedding = embeddingModel.embed(segment).content();
            embeddingStore.add(embedding, segment);
        }

        long cost = System.currentTimeMillis() - start;
        log.info("[SchemaService] 字段组向量化完成，耗时{}ms", cost);
    }

    /**
     * 根据用户问题匹配最相关的字段组
     *
     * @param userQuestion 用户原始问题
     * @return 匹配到的字段组列表（按相似度降序）
     */
    public List<FieldGroup> matchGroups(String userQuestion) {
        if (userQuestion == null || userQuestion.trim().isEmpty()) {
            return Collections.emptyList();
        }

        // 1. 向量化用户问题
        Embedding questionEmbedding = embeddingModel.embed(userQuestion).content();

        // 2. 在Store中检索最相关的字段组
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(questionEmbedding)
                .maxResults(MAX_MATCH_COUNT)
                .minScore(SIMILARITY_THRESHOLD)
                .build();

        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);
        List<EmbeddingMatch<TextSegment>> matches = searchResult.matches();

        if (matches.isEmpty()) {
            log.warn("[SchemaService] 未找到相似度超过阈值的字段组, question={}", userQuestion);
            return Collections.emptyList();
        }

        // 3. 根据匹配到的语义描述，找到对应的 FieldGroup
        List<FieldGroup> matchedGroups = matches.stream()
                .map(match -> {
                    String matchedDescription = match.embedded().text();
                    return findGroupByDescription(matchedDescription);
                })
                .filter(group -> group != null)
                .collect(Collectors.toList());

        log.info("[SchemaService] 用户问题匹配到{}个字段组: {}, question={}",
                matchedGroups.size(),
                matchedGroups.stream().map(FieldGroup::getGroupName).collect(Collectors.joining(",")),
                userQuestion);

        return matchedGroups;
    }

    /**
     * 将匹配到的字段组拼接成Prompt注入内容
     *
     * @param matchedGroups 匹配到的字段组
     * @return Prompt中的Schema描述文本
     */
    public String buildSchemaContext(List<FieldGroup> matchedGroups) {
        if (matchedGroups == null || matchedGroups.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("表名：report_reservation_real_time（机票订单实时报表宽表）\n\n");

        for (FieldGroup group : matchedGroups) {
            sb.append(group.getFieldDetail()).append("\n\n");
        }

        return sb.toString();
    }

    /**
     * 获取匹配到的字段组名称（逗号分隔，用于记录日志）
     */
    public String getMatchedGroupNames(List<FieldGroup> matchedGroups) {
        if (matchedGroups == null || matchedGroups.isEmpty()) {
            return "";
        }
        return matchedGroups.stream()
                .map(FieldGroup::getGroupName)
                .collect(Collectors.joining(","));
    }

    // ── private ──────────────────────────────────────────────

    private FieldGroup findGroupByDescription(String description) {
        for (FieldGroup group : allGroups) {
            if (group.getSemanticDescription().equals(description)) {
                return group;
            }
        }
        return null;
    }
}
