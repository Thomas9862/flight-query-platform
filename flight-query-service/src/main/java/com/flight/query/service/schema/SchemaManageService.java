package com.flight.query.service.schema;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.flight.query.domain.entity.SchemaFieldGroup;
import com.flight.query.domain.mapper.SchemaFieldGroupMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Schema 字段组管理服务
 * <p>
 * 提供 CRUD 操作，每次数据变更自动触发 SchemaService.reload() 重新向量化。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SchemaManageService {

    private final SchemaFieldGroupMapper fieldGroupMapper;
    private final SchemaService schemaService;

    public List<SchemaFieldGroup> listEnabled() {
        return fieldGroupMapper.selectList(
                new LambdaQueryWrapper<SchemaFieldGroup>()
                        .eq(SchemaFieldGroup::getStatus, SchemaFieldGroup.STATUS_ENABLED)
                        .orderByAsc(SchemaFieldGroup::getSortOrder));
    }

    public List<SchemaFieldGroup> listAll() {
        return fieldGroupMapper.selectList(
                new LambdaQueryWrapper<SchemaFieldGroup>()
                        .orderByAsc(SchemaFieldGroup::getSortOrder));
    }

    public SchemaFieldGroup getById(Long id) {
        return fieldGroupMapper.selectById(id);
    }

    public SchemaFieldGroup add(SchemaFieldGroup entity) {
        if (entity.getStatus() == null) {
            entity.setStatus(SchemaFieldGroup.STATUS_ENABLED);
        }
        if (entity.getTableName() == null || entity.getTableName().isEmpty()) {
            entity.setTableName("report_reservation_real_time");
        }
        fieldGroupMapper.insert(entity);
        log.info("[SchemaManageService] 新增字段组: id={}, name={}", entity.getId(), entity.getGroupName());

        schemaService.reload();
        return entity;
    }

    public SchemaFieldGroup update(SchemaFieldGroup entity) {
        fieldGroupMapper.updateById(entity);
        log.info("[SchemaManageService] 更新字段组: id={}, name={}", entity.getId(), entity.getGroupName());

        schemaService.reload();
        return entity;
    }

    public void delete(Long id) {
        fieldGroupMapper.deleteById(id);
        log.info("[SchemaManageService] 删除字段组: id={}", id);

        schemaService.reload();
    }
}
