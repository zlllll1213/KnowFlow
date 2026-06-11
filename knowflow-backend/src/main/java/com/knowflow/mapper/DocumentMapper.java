package com.knowflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.entity.Document;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface DocumentMapper extends BaseMapper<Document> {

    @Select("""
            SELECT COUNT(*)
            FROM document_chunk dc
            JOIN document d ON d.id = dc.document_id
            JOIN knowledge_base kb ON kb.id = d.kb_id
            WHERE d.user_id = #{userId}
              AND kb.user_id = #{userId}
              AND d.is_deleted = 0
              AND kb.is_deleted = 0
            """)
    Long countChunksByUser(@Param("userId") Long userId);

    @Select("""
            <script>
            SELECT kb_id,
                   COUNT(*) AS document_count,
                   SUM(CASE WHEN status = 'DONE' THEN 1 ELSE 0 END) AS done_count
            FROM document
            WHERE user_id = #{userId}
              AND is_deleted = 0
              AND kb_id IN
              <foreach collection="kbIds" item="kbId" open="(" separator="," close=")">
                #{kbId}
              </foreach>
            GROUP BY kb_id
            </script>
            """)
    List<Map<String, Object>> selectKbDocumentCounts(@Param("userId") Long userId,
                                                     @Param("kbIds") List<Long> kbIds);
}
