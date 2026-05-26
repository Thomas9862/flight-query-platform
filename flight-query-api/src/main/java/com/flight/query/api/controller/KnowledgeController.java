package com.flight.query.api.controller;

import com.flight.query.api.dto.KnowledgeRequest;
import com.flight.query.common.result.Result;
import com.flight.query.domain.entity.KnowledgeBase;
import com.flight.query.service.knowledge.KnowledgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 知识库管理接口
 * <p>
 * 提供 CRUD 操作，数据变更自动同步到 Elasticsearch 向量索引。
 */
@Slf4j
@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    /**
     * 查询所有启用的知识条目
     */
    @GetMapping
    public Result<List<KnowledgeBase>> list() {
        return Result.success(knowledgeService.listEnabled());
    }

    /**
     * 查询所有知识条目（含禁用）
     */
    @GetMapping("/all")
    public Result<List<KnowledgeBase>> listAll() {
        return Result.success(knowledgeService.listAll());
    }

    /**
     * 按分类查询
     */
    @GetMapping("/category/{category}")
    public Result<List<KnowledgeBase>> listByCategory(@PathVariable String category) {
        return Result.success(knowledgeService.listByCategory(category));
    }

    /**
     * 根据ID查询
     */
    @GetMapping("/{id}")
    public Result<KnowledgeBase> getById(@PathVariable Long id) {
        KnowledgeBase kb = knowledgeService.getById(id);
        if (kb == null) {
            return Result.fail("知识条目不存在");
        }
        return Result.success(kb);
    }

    /**
     * 新增知识条目
     */
    @PostMapping
    public Result<KnowledgeBase> add(@RequestBody KnowledgeRequest request) {
        log.info("[KnowledgeController] 新增知识: category={}, title={}",
                request.getCategory(), request.getTitle());

        KnowledgeBase kb = new KnowledgeBase();
        kb.setCategory(request.getCategory());
        kb.setTitle(request.getTitle());
        kb.setContent(request.getContent());

        return Result.success(knowledgeService.add(kb));
    }

    /**
     * 更新知识条目
     */
    @PutMapping("/{id}")
    public Result<KnowledgeBase> update(@PathVariable Long id,
                                        @RequestBody KnowledgeRequest request) {
        log.info("[KnowledgeController] 更新知识: id={}", id);

        KnowledgeBase kb = knowledgeService.getById(id);
        if (kb == null) {
            return Result.fail("知识条目不存在");
        }

        if (request.getCategory() != null) {
            kb.setCategory(request.getCategory());
        }
        if (request.getTitle() != null) {
            kb.setTitle(request.getTitle());
        }
        if (request.getContent() != null) {
            kb.setContent(request.getContent());
        }
        if (request.getStatus() != null) {
            kb.setStatus(request.getStatus());
        }

        return Result.success(knowledgeService.update(kb));
    }

    /**
     * 删除知识条目
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        log.info("[KnowledgeController] 删除知识: id={}", id);
        knowledgeService.delete(id);
        return Result.success();
    }

    /**
     * 手动触发全量重建 ES 索引
     */
    @PostMapping("/rebuild-index")
    public Result<String> rebuildIndex() {
        log.info("[KnowledgeController] 手动触发ES索引重建");
        int count = knowledgeService.rebuildEsIndex();
        return Result.success("ES索引重建完成，共 " + count + " 条知识");
    }
}
