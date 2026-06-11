package com.knowflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.entity.ParseTask;
import com.knowflow.vo.RecentFailedTaskVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ParseTaskMapper extends BaseMapper<ParseTask> {

    @Select("""
            SELECT pt.id AS task_id,
                   d.id AS document_id,
                   d.file_name,
                   COALESCE(NULLIF(pt.error_message, ''), '未知错误') AS error_message,
                   pt.updated_at
            FROM parse_task pt
            JOIN document d ON d.id = pt.document_id
            JOIN knowledge_base kb ON kb.id = d.kb_id
            WHERE d.user_id = #{userId}
              AND kb.user_id = #{userId}
              AND pt.status = 'FAILED'
              AND pt.is_deleted = 0
              AND d.is_deleted = 0
              AND kb.is_deleted = 0
            ORDER BY pt.updated_at DESC
            LIMIT #{limit}
            """)
    List<RecentFailedTaskVO> selectRecentFailedByUser(@Param("userId") Long userId, @Param("limit") int limit);
}
