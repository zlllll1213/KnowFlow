package com.knowflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {

    @Select("""
            SELECT cs.id,
                   cs.kb_id,
                   cs.user_id,
                   cs.title,
                   cs.is_deleted,
                   cs.created_at,
                   cs.updated_at
            FROM chat_session cs
            JOIN knowledge_base kb ON kb.id = cs.kb_id
            WHERE cs.user_id = #{userId}
              AND kb.user_id = #{userId}
              AND cs.is_deleted = 0
              AND kb.is_deleted = 0
            ORDER BY cs.updated_at DESC
            LIMIT #{limit}
            """)
    List<ChatSession> selectRecentByUser(@Param("userId") Long userId, @Param("limit") int limit);
}
