package com.knowflow.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 本地文件存储实现 — 第一版使用本地目录。
 * 后续可替换为 MinIO 实现（实现 MinioStorageService 接口即可）。
 */
@Slf4j
@Component
public class LocalStorageService implements MinioStorageService {

    private static final String BASE_DIR = "./storage";

    @Override
    public String upload(MultipartFile file, String objectName) {
        try {
            Path dir = Paths.get(BASE_DIR);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            Path target = dir.resolve(objectName);
            file.transferTo(target.toFile());
            log.info("文件已保存到: {}", target.toAbsolutePath());
            return target.toString();
        } catch (IOException e) {
            throw new RuntimeException("本地文件保存失败: " + e.getMessage(), e);
        }
    }

    @Override
    public InputStream download(String objectName) {
        try {
            Path target = Paths.get(BASE_DIR).resolve(objectName);
            return Files.newInputStream(target);
        } catch (IOException e) {
            throw new RuntimeException("文件读取失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String objectName) {
        try {
            Path target = Paths.get(BASE_DIR).resolve(objectName);
            Files.deleteIfExists(target);
        } catch (IOException e) {
            log.warn("文件删除失败: {}", objectName, e);
        }
    }
}
