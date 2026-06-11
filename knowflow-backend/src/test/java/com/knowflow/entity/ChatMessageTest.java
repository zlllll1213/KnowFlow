package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.knowflow.dto.RagSourceChunk;
import com.knowflow.util.RagSourceChunkListTypeHandler;
import org.junit.jupiter.api.Test;

import java.lang.reflect.ParameterizedType;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatMessageTest {

    @Test
    void sourcesIsMappedAsTypedRagSourceChunkList() throws NoSuchFieldException {
        var field = ChatMessage.class.getDeclaredField("sources");
        var genericType = (ParameterizedType) field.getGenericType();

        assertThat(field.getType()).isEqualTo(List.class);
        assertThat(genericType.getActualTypeArguments()[0]).isEqualTo(RagSourceChunk.class);
        assertThat(field.getAnnotation(TableField.class).typeHandler())
                .isEqualTo(RagSourceChunkListTypeHandler.class);
    }
}
