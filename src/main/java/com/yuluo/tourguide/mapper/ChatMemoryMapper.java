package com.yuluo.tourguide.mapper;

import com.mybatisflex.core.BaseMapper;
import com.yuluo.tourguide.model.entity.ChatMemoryEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 对话记忆表 Mapper，提供会话列表聚合、历史查询与删除
 */
@Mapper
public interface ChatMemoryMapper extends BaseMapper<ChatMemoryEntity> {
}