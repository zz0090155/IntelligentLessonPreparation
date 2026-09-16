package com.example.backend.service;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.InputStream;
import java.util.UUID;

@Service
public class OssService {
    @Value("${aliyun.oss.endpoint}")
    private String endpoint;
    @Value("${aliyun.oss.accessKeyId}")
    private String accessKeyId;
    @Value("${aliyun.oss.accessKeySecret}")
    private String accessKeySecret;
    @Value("${aliyun.oss.bucketName}")
    private String bucketName;

    public String uploadFile(MultipartFile file, String extension) throws Exception {
        OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        String fileName = "student-uploads/" + UUID.randomUUID().toString().replace("-", "") + extension;

        try (InputStream inputStream = file.getInputStream()) {
            ossClient.putObject(bucketName, fileName, inputStream);
            return fileName;
        } finally {
            if (ossClient != null) {
                ossClient.shutdown();
            }
        }
    }

    public String uploadLocalFile(File file) {
        OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);

        String fileName = file.getName();
        String extension = "";
        if (fileName.lastIndexOf(".") != -1) {
            extension = fileName.substring(fileName.lastIndexOf(".")).toLowerCase();
        }

        String objectName = "avatar/" + UUID.randomUUID().toString().replace("-", "") + extension;

        try {
            ossClient.putObject(bucketName, objectName, file);
            return "https://" + bucketName + "." + endpoint + "/" + objectName;
        } catch (Exception e) {
            System.err.println("OSS 上传本地文件失败: " + e.getMessage());
            return null;
        } finally {
            if (ossClient != null) {
                ossClient.shutdown();
            }
        }
    }
}