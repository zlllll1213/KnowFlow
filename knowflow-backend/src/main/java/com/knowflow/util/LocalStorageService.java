package com.knowflow.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 本地文件存储实现。
 * 当 storage.type=local 时生效。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "storage.type", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements FileStorageService {

    private final Path baseDir;

    public LocalStorageService(@Value("${storage.local-path:./storage}") String baseDir) {
        this.baseDir = Paths.get(baseDir).toAbsolutePath().normalize();
    }

    @Override
    public String upload(MultipartFile file, String objectKey) {
        try {
            Files.createDirectories(baseDir);

            Path target = resolveObjectPath(objectKey);
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            file.transferTo(target.toFile());
            log.info("文件已保存到: {}", target.toAbsolutePath());
            return objectKey;
        } catch (IOException e) {
            throw new RuntimeException("本地文件保存失败: " + e.getMessage(), e);
        }
    }

    @Override
    public InputStream download(String objectKey) {
        try {
            Path target = resolveObjectPath(objectKey);
            return Files.newInputStream(target);
        } catch (IOException e) {
            throw new RuntimeException("文件读取失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            Path target = resolveObjectPath(objectKey);
            Files.deleteIfExists(target);
        } catch (Exception e) {
            log.warn("文件删除失败: {}", objectKey, e);
        }
    }

    private Path resolveObjectPath(String objectKey) {
        Path keyPath = Paths.get(objectKey).normalize();

        if (keyPath.isAbsolute()) {
            if (!keyPath.startsWith(baseDir)) {
                throw new IllegalArgumentException("非法文件路径: " + objectKey);
            }
            return keyPath;
        }

        Path normalized = keyPath;
        if (normalized.getNameCount() == 1 && "storage".equals(normalized.getName(0).toString())) {
            throw new IllegalArgumentException("非法文件路径: " + objectKey);
        }
        if (normalized.getNameCount() > 1 && "storage".equals(normalized.getName(0).toString())) {
            normalized = normalized.subpath(1, normalized.getNameCount());
        }

        Path resolved = baseDir.resolve(normalized).normalize();
        if (!resolved.startsWith(baseDir)) {
            throw new IllegalArgumentException("非法文件路径: " + objectKey);
        }
        return resolved;
    }
}
