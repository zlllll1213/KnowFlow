package com.knowflow.util;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * 文件存储接口 — 支持本地存储和 MinIO 两种实现。
 */
public interface MinioStorageService {

    /** 上传文件，返回存储路径 */
    String upload(MultipartFile file, String objectName);

    /** 下载文件 */
    InputStream download(String objectName);

    /** 删除文件 */
    void delete(String objectName);
}
