package com.knowflow.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.dto.RagSourceChunk;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.postgresql.util.PGobject;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

public class RagSourceChunkListTypeHandler extends BaseTypeHandler<List<RagSourceChunk>> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<RagSourceChunk>> SOURCE_CHUNK_LIST_TYPE = new TypeReference<>() {
    };

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<RagSourceChunk> parameter, JdbcType jdbcType)
            throws SQLException {
        PGobject jsonObject = new PGobject();
        jsonObject.setType("jsonb");
        jsonObject.setValue(toJson(parameter));
        ps.setObject(i, jsonObject);
    }

    @Override
    public List<RagSourceChunk> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return fromJson(rs.getString(columnName));
    }

    @Override
    public List<RagSourceChunk> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return fromJson(rs.getString(columnIndex));
    }

    @Override
    public List<RagSourceChunk> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return fromJson(cs.getString(columnIndex));
    }

    private String toJson(List<RagSourceChunk> sources) throws SQLException {
        try {
            return OBJECT_MAPPER.writeValueAsString(sources == null ? Collections.emptyList() : sources);
        } catch (JsonProcessingException e) {
            throw new SQLException("sources JSON 序列化失败", e);
        }
    }

    private List<RagSourceChunk> fromJson(String value) throws SQLException {
        if (value == null || value.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return OBJECT_MAPPER.readValue(value, SOURCE_CHUNK_LIST_TYPE);
        } catch (JsonProcessingException e) {
            // sources 是审计/溯源数据，遇到坏数据时显式失败，避免静默丢失上下文。
            throw new SQLException("sources JSON 解析失败", e);
        }
    }
}
