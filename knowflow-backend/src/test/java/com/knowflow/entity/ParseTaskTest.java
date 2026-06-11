package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ParseTaskTest {

    @Test
    void parseTaskUsesLogicalDeleteFlag() throws NoSuchFieldException {
        assertThat(ParseTask.class.getDeclaredField("isDeleted").getAnnotation(TableLogic.class))
                .isNotNull();
    }
}
