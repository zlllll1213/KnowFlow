package com.knowflow.util;

import com.knowflow.dto.RagSourceChunk;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PGobject;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagSourceChunkListTypeHandlerTest {

    private final RagSourceChunkListTypeHandler handler = new RagSourceChunkListTypeHandler();

    @Test
    void writesSourcesAsPostgresJsonb() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        RagSourceChunk source = new RagSourceChunk(1L, 2L, "guide.pdf", 3,
                "matched content", 0.92);

        handler.setNonNullParameter(ps, 1, List.of(source), JdbcType.OTHER);

        verify(ps).setObject(eq(1), org.mockito.ArgumentMatchers.argThat(value -> {
            assertThat(value).isInstanceOf(PGobject.class);
            PGobject json = (PGobject) value;
            assertThat(json.getType()).isEqualTo("jsonb");
            assertThat(json.getValue()).contains("\"chunkId\":1", "\"fileName\":\"guide.pdf\"");
            return true;
        }));
    }

    @Test
    void readsSourcesFromJsonbString() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("sources")).thenReturn("""
                [{"chunkId":1,"documentId":2,"fileName":"guide.pdf","chunkIndex":3,"content":"matched content","score":0.92}]
                """);

        List<RagSourceChunk> sources = handler.getNullableResult(rs, "sources");

        assertThat(sources).hasSize(1);
        assertThat(sources.get(0).getChunkId()).isEqualTo(1L);
        assertThat(sources.get(0).getFileName()).isEqualTo("guide.pdf");
        assertThat(sources.get(0).getScore()).isEqualTo(0.92);
    }
}
