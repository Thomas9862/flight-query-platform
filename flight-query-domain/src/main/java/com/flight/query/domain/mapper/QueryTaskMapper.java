package com.flight.query.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flight.query.domain.entity.QueryTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 查询任务Mapper
 */
@Mapper
public interface QueryTaskMapper extends BaseMapper<QueryTask> {
}
