package com.knowflow.util;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class MinioStorageServiceImplTest {

    @Test
    void bucketReadinessUsesAtomicBoolean() throws NoSuchFieldException {
        assertThat(MinioStorageServiceImpl.class.getDeclaredField("bucketReady").getType())
                .isEqualTo(AtomicBoolean.class);
    }
}
