package com.flight.query.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flight.query.domain.entity.KnowledgeBase;
import org.apache.ibatis.annotations.Mapper;

/**
 * 业务知识库Mapper
 */
@Mapper
public interface KnowledgeBaseMapper extends BaseMapper<KnowledgeBase> {
}
