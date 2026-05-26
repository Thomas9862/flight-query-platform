package com.flight.query.api.controller;

import com.flight.query.api.dto.SchemaFieldGroupRequest;
import com.flight.query.common.result.Result;
import com.flight.query.domain.entity.SchemaFieldGroup;
import com.flight.query.service.schema.SchemaManageService;
import com.flight.query.service.schema.SchemaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Schema 字段组管理接口
 * <p>
 * 提供字段组的 CRUD 操作，数据变更自动触发向量重载。
 * 运营/开发可通过此接口动态扩展查询字段，无需改代码。
 */
@Slf4j
@RestController
@RequestMapping("/api/schema")
@RequiredArgsConstructor
public class SchemaController {

    private final SchemaManageService schemaManageService;
    private final SchemaService schemaService;

    @GetMapping
    public Result<List<SchemaFieldGroup>> list() {
        return Result.success(schemaManageService.listEnabled());
    }

    @GetMapping("/all")
    public Result<List<SchemaFieldGroup>> listAll() {
        return Result.success(schemaManageService.listAll());
    }

    @GetMapping("/{id}")
    public Result<SchemaFieldGroup> getById(@PathVariable Long id) {
        SchemaFieldGroup entity = schemaManageService.getById(id);
        if (entity == null) {
            return Result.fail("字段组不存在");
        }
        return Result.success(entity);
    }

    @PostMapping
    public Result<SchemaFieldGroup> add(@RequestBody SchemaFieldGroupRequest request) {
        log.info("[SchemaController] 新增字段组: name={}", request.getGroupName());

        SchemaFieldGroup entity = new SchemaFieldGroup();
        entity.setTableName(request.getTableName());
        entity.setGroupName(request.getGroupName());
        entity.setSemanticDesc(request.getSemanticDesc());
        entity.setFieldDetail(request.getFieldDetail());
        entity.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);

        return Result.success(schemaManageService.add(entity));
    }

    @PutMapping("/{id}")
    public Result<SchemaFieldGroup> update(@PathVariable Long id,
                                           @RequestBody SchemaFieldGroupRequest request) {
        log.info("[SchemaController] 更新字段组: id={}", id);

        SchemaFieldGroup entity = schemaManageService.getById(id);
        if (entity == null) {
            return Result.fail("字段组不存在");
        }

        if (request.getTableName() != null) entity.setTableName(request.getTableName());
        if (request.getGroupName() != null) entity.setGroupName(request.getGroupName());
        if (request.getSemanticDesc() != null) entity.setSemanticDesc(request.getSemanticDesc());
        if (request.getFieldDetail() != null) entity.setFieldDetail(request.getFieldDetail());
        if (request.getStatus() != null) entity.setStatus(request.getStatus());
        if (request.getSortOrder() != null) entity.setSortOrder(request.getSortOrder());

        return Result.success(schemaManageService.update(entity));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        log.info("[SchemaController] 删除字段组: id={}", id);
        schemaManageService.delete(id);
        return Result.success();
    }

    /**
     * 手动触发重新加载向量
     */
    @PostMapping("/reload")
    public Result<String> reload() {
        log.info("[SchemaController] 手动触发字段组重载");
        schemaService.reload();
        return Result.success("字段组重载完成");
    }
}
