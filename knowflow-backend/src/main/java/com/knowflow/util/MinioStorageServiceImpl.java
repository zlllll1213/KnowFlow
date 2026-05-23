package com.knowflow.util;

import com.knowflow.config.MinioConfig;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * MinIO 对象存储实现。
 * 当 storage.type=minio 时生效。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "storage.type", havingValue = "minio")
@RequiredArgsConstructor
public class MinioStorageServiceImpl implements FileStorageService {

    private final MinioClient minioClient;
    private final MinioConfig minioConfig;

    @Override
    public String upload(MultipartFile file, String objectKey) {
        try {
            String bucket = minioConfig.getBucket();
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build());
            log.info("文件已上传至 MinIO: bucket={}, objectKey={}", bucket, objectKey);
            return objectKey;
        } catch (Exception e) {
            throw new RuntimeException("MinIO 文件上传失败: " + e.getMessage(), e);
        }
    }

    @Override
    public InputStream download(String objectKey) {
        try {
            String bucket = minioConfig.getBucket();
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build());
        } catch (Exception e) {
            throw new RuntimeException("MinIO 文件下载失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            String bucket = minioConfig.getBucket();
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build());
            log.info("MinIO 文件已删除: bucket={}, objectKey={}", bucket, objectKey);
        } catch (Exception e) {
            log.warn("MinIO 文件删除失败: {}", objectKey, e);
        }
    }
}
