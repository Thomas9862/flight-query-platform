package com.flight.query.service.schema;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.flight.query.domain.entity.SchemaFieldGroup;
import com.flight.query.domain.mapper.SchemaFieldGroupMapper;
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
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Schema 语义匹配服务
 * <p>
 * 从 MySQL schema_field_group 表加载字段组，向量化后存入 InMemoryEmbeddingStore。
 * 字段组数据量小（8~30个），InMemory 足够；支持 reload() 热重载。
 */
@Slf4j
@Service
public class SchemaService {

    private final InMemoryEmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final SchemaFieldGroupMapper fieldGroupMapper;

    /** 内存中的字段组列表（从 MySQL 加载） */
    private List<FieldGroup> allGroups;

    /** 语义描述 → 表名 的映射（用于 buildSchemaContext 动态获取表名） */
    private Map<String, String> descToTableName;

    private static final double SIMILARITY_THRESHOLD = 0.5;
    private static final int MAX_MATCH_COUNT = 3;

    public SchemaService(EmbeddingModel embeddingModel, SchemaFieldGroupMapper fieldGroupMapper) {
        this.embeddingModel = embeddingModel;
        this.fieldGroupMapper = fieldGroupMapper;
        this.embeddingStore = new InMemoryEmbeddingStore<>();
    }

    /**
     * 启动时从 MySQL 加载字段组并向量化
     */
    @PostConstruct
    public void init() {
        reload();
    }

    /**
     * 重新加载：清空内存 → 从 MySQL 读取 → 向量化 → 存入 Store
     * CRUD 操作后调用此方法热重载
     */
    public synchronized void reload() {
        log.info("[SchemaService] 从 MySQL 加载字段组...");
        long start = System.currentTimeMillis();

        // 1. 从 MySQL 加载所有启用的字段组
        List<SchemaFieldGroup> entities = fieldGroupMapper.selectList(
                new LambdaQueryWrapper<SchemaFieldGroup>()
                        .eq(SchemaFieldGroup::getStatus, SchemaFieldGroup.STATUS_ENABLED)
                        .orderByAsc(SchemaFieldGroup::getSortOrder));

        // 2. 转为内存 POJO
        this.allGroups = entities.stream()
                .map(e -> new FieldGroup(e.getGroupName(), e.getSemanticDesc(), e.getFieldDetail()))
                .collect(Collectors.toList());

        // 3. 构建语义描述 → 表名的映射
        this.descToTableName = entities.stream()
                .collect(Collectors.toMap(
                        SchemaFieldGroup::getSemanticDesc,
                        SchemaFieldGroup::getTableName,
                        (a, b) -> a));

        // 4. 清空旧向量 → 重新向量化
        embeddingStore.removeAll();

        for (FieldGroup group : allGroups) {
            TextSegment segment = TextSegment.from(group.getSemanticDescription());
            Embedding embedding = embeddingModel.embed(segment).content();
            embeddingStore.add(embedding, segment);
        }

        long cost = System.currentTimeMillis() - start;
        log.info("[SchemaService] 字段组加载完成, 共{}组, 耗时{}ms", allGroups.size(), cost);
    }

    /**
     * 根据用户问题匹配最相关的字段组
     */
    public List<FieldGroup> matchGroups(String userQuestion) {
        if (userQuestion == null || userQuestion.trim().isEmpty()) {
            return Collections.emptyList();
        }

        Embedding questionEmbedding = embeddingModel.embed(userQuestion).content();

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

        List<FieldGroup> matchedGroups = matches.stream()
                .map(match -> findGroupByDescription(match.embedded().text()))
                .filter(group -> group != null)
                .collect(Collectors.toList());

        log.info("[SchemaService] 匹配到{}个字段组: {}, question={}",
                matchedGroups.size(),
                matchedGroups.stream().map(FieldGroup::getGroupName).collect(Collectors.joining(",")),
                userQuestion);

        return matchedGroups;
    }

    /**
     * 将匹配到的字段组拼接成 Prompt 注入内容
     * 表名从 MySQL 动态获取，不再硬编码
     */
    public String buildSchemaContext(List<FieldGroup> matchedGroups) {
        if (matchedGroups == null || matchedGroups.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();

        // 动态获取表名（取第一个匹配组的表名，多表场景可扩展）
        String tableName = descToTableName.getOrDefault(
                matchedGroups.get(0).getSemanticDescription(),
                "report_reservation_real_time");
        sb.append("表名：").append(tableName).append("（机票订单实时报表宽表）\n\n");

        for (FieldGroup group : matchedGroups) {
            sb.append(group.getFieldDetail()).append("\n\n");
        }

        return sb.toString();
    }

    /**
     * 获取匹配到的字段组名称（逗号分隔，用于日志）
     */
    public String getMatchedGroupNames(List<FieldGroup> matchedGroups) {
        if (matchedGroups == null || matchedGroups.isEmpty()) {
            return "";
        }
        return matchedGroups.stream()
                .map(FieldGroup::getGroupName)
                .collect(Collectors.joining(","));
    }

    private FieldGroup findGroupByDescription(String description) {
        for (FieldGroup group : allGroups) {
            if (group.getSemanticDescription().equals(description)) {
                return group;
            }
        }
        return null;
    }
}
