package com.flight.query.service.knowledge;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.flight.query.domain.entity.KnowledgeBase;
import com.flight.query.domain.mapper.KnowledgeBaseMapper;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 知识库管理服务
 * <p>
 * MySQL 存储知识原文（CRUD），Elasticsearch 存储向量（检索）。
 * 启动时从 MySQL 加载所有启用的知识条目，向量化后写入 ES。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeService {

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    // ── 启动同步 ──────────────────────────────────────────────

    @PostConstruct
    public void syncOnStartup() {
        log.info("[KnowledgeService] 启动知识库同步: MySQL → Elasticsearch ...");
        long start = System.currentTimeMillis();

        try {
            List<KnowledgeBase> knowledgeList = listEnabled();
            int count = 0;

            for (KnowledgeBase kb : knowledgeList) {
                String text = buildSegmentText(kb);
                TextSegment segment = TextSegment.from(text);
                Embedding embedding = embeddingModel.embed(segment).content();
                embeddingStore.add(embedding, segment);
                count++;
            }

            long cost = System.currentTimeMillis() - start;
            log.info("[KnowledgeService] 知识库同步完成, 共{}条知识, 耗时{}ms", count, cost);
        } catch (Exception e) {
            log.error("[KnowledgeService] 知识库同步失败，RAG功能可能不可用", e);
        }
    }

    // ── CRUD 操作 ─────────────────────────────────────────────

    /**
     * 查询所有启用的知识条目
     */
    public List<KnowledgeBase> listEnabled() {
        return knowledgeBaseMapper.selectList(
                new LambdaQueryWrapper<KnowledgeBase>()
                        .eq(KnowledgeBase::getStatus, KnowledgeBase.STATUS_ENABLED)
                        .orderByAsc(KnowledgeBase::getCategory)
                        .orderByAsc(KnowledgeBase::getId));
    }

    /**
     * 按分类查询知识条目
     */
    public List<KnowledgeBase> listByCategory(String category) {
        return knowledgeBaseMapper.selectList(
                new LambdaQueryWrapper<KnowledgeBase>()
                        .eq(KnowledgeBase::getCategory, category)
                        .eq(KnowledgeBase::getStatus, KnowledgeBase.STATUS_ENABLED)
                        .orderByAsc(KnowledgeBase::getId));
    }

    /**
     * 查询所有知识条目（含禁用）
     */
    public List<KnowledgeBase> listAll() {
        return knowledgeBaseMapper.selectList(
                new LambdaQueryWrapper<KnowledgeBase>()
                        .orderByAsc(KnowledgeBase::getCategory)
                        .orderByAsc(KnowledgeBase::getId));
    }

    /**
     * 根据ID查询
     */
    public KnowledgeBase getById(Long id) {
        return knowledgeBaseMapper.selectById(id);
    }

    /**
     * 新增知识条目，并同步到 ES
     */
    public KnowledgeBase add(KnowledgeBase kb) {
        kb.setStatus(KnowledgeBase.STATUS_ENABLED);
        knowledgeBaseMapper.insert(kb);
        log.info("[KnowledgeService] 新增知识: id={}, title={}", kb.getId(), kb.getTitle());

        syncSingleToEs(kb);
        return kb;
    }

    /**
     * 更新知识条目，并全量重建 ES 索引
     */
    public KnowledgeBase update(KnowledgeBase kb) {
        knowledgeBaseMapper.updateById(kb);
        log.info("[KnowledgeService] 更新知识: id={}, title={}", kb.getId(), kb.getTitle());

        rebuildEsIndex();
        return kb;
    }

    /**
     * 删除知识条目（物理删除），并全量重建 ES 索引
     */
    public void delete(Long id) {
        knowledgeBaseMapper.deleteById(id);
        log.info("[KnowledgeService] 删除知识: id={}", id);

        rebuildEsIndex();
    }

    /**
     * 手动触发全量重建 ES 索引
     */
    public int rebuildEsIndex() {
        log.info("[KnowledgeService] 开始全量重建ES索引...");
        long start = System.currentTimeMillis();

        embeddingStore.removeAll();

        List<KnowledgeBase> knowledgeList = listEnabled();
        int count = 0;

        for (KnowledgeBase kb : knowledgeList) {
            String text = buildSegmentText(kb);
            TextSegment segment = TextSegment.from(text);
            Embedding embedding = embeddingModel.embed(segment).content();
            embeddingStore.add(embedding, segment);
            count++;
        }

        long cost = System.currentTimeMillis() - start;
        log.info("[KnowledgeService] ES索引重建完成, 共{}条, 耗时{}ms", count, cost);
        return count;
    }

    // ── 私有方法 ──────────────────────────────────────────────

    /**
     * 单条知识同步到 ES（新增场景）
     */
    private void syncSingleToEs(KnowledgeBase kb) {
        try {
            String text = buildSegmentText(kb);
            TextSegment segment = TextSegment.from(text);
            Embedding embedding = embeddingModel.embed(segment).content();
            embeddingStore.add(embedding, segment);
        } catch (Exception e) {
            log.warn("[KnowledgeService] 单条知识同步ES失败, id={}", kb.getId(), e);
        }
    }

    /**
     * 构建向量化文本：标题 + 内容，提升检索精度
     */
    private String buildSegmentText(KnowledgeBase kb) {
        return String.format("[%s] %s\n%s", kb.getCategory(), kb.getTitle(), kb.getContent());
    }
}
