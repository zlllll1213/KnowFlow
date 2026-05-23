package com.knowflow.util;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * 文件存储抽象接口。
 * 实现类：LocalStorageService（本地）、MinioStorageServiceImpl（MinIO 对象存储）。
 */
public interface FileStorageService {

    /**
     * 上传文件。
     * @param file       上传的文件
     * @param objectKey  存储 key（本地为相对路径，MinIO 为 object name）
     * @return 存储后的访问路径或 key
     */
    String upload(MultipartFile file, String objectKey);

    /** 下载文件 */
    InputStream download(String objectKey);

    /** 删除文件 */
    void delete(String objectKey);
}
