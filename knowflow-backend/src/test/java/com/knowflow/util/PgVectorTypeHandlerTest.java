package com.knowflow.util;

import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PGobject;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PgVectorTypeHandlerTest {

    private final PgVectorTypeHandler handler = new PgVectorTypeHandler();

    @Test
    void writesFloatArrayAsPostgresVector() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);

        handler.setNonNullParameter(ps, 1, new float[]{0.1f, -0.2f, 3.5f}, JdbcType.OTHER);

        verify(ps).setObject(eq(1), org.mockito.ArgumentMatchers.argThat(value -> {
            assertThat(value).isInstanceOf(PGobject.class);
            PGobject vector = (PGobject) value;
            assertThat(vector.getType()).isEqualTo("vector");
            assertThat(vector.getValue()).isEqualTo("[0.1,-0.2,3.5]");
            return true;
        }));
    }

    @Test
    void readsFloatArrayFromPostgresVectorString() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("embedding")).thenReturn("[0.1, -0.2, 3.5]");

        float[] vector = handler.getNullableResult(rs, "embedding");

        assertThat(vector).containsExactly(0.1f, -0.2f, 3.5f);
    }
}
