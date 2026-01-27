package com.faers.agent.utils;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.oss.OSS;
import com.aliyun.oss.model.ObjectMetadata;
import com.faers.agent.config.AliyunOssConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 阿里云平台api解析文件
 * @author zgm
 */
@Slf4j
@Component
public class FileParsingUtils {
    @Autowired
    private OSS ossClient;

    @Autowired
    private AliyunOssConfig aliyunOssConfig;

    /**
     * 文档解析（大模型版）
     * @param userId 用户id
     * @param markdown 文件
     * @return 相关信息
     */
    public String upload(String parentId, String userId, String markdown) {
        // 1. 参数校验
        if (StringUtils.isBlank(markdown)) {
            log.warn("⚠️ 忽略空 Markdown 上传 | userId={}, parentId={}", userId, parentId);
            return "";
        }

        // 2. 统一编码
        byte[] contentBytes = markdown.getBytes(StandardCharsets.UTF_8);
        if (contentBytes.length == 0) {
            log.warn("⚠️ 编码后字节为空 | userId={}, parentId={}", userId, parentId);
            return "";
        }

        // 3. 构建路径
        String objectKey = String.format("%s/%s/%s.md",
                aliyunOssConfig.getFileHost(), userId, parentId);

        try (ByteArrayInputStream input = new ByteArrayInputStream(contentBytes)) {
            ObjectMetadata meta = new ObjectMetadata();
            meta.setContentLength(contentBytes.length);
            meta.setContentType("text/markdown; charset=utf-8");

            ossClient.putObject(aliyunOssConfig.getBucketName(), objectKey, input, meta);
            log.info("✅ Markdown 上传成功 | size={} bytes, key={}", contentBytes.length, objectKey);

            return aliyunOssConfig.getFileUrl() + "/" + objectKey;
        } catch (Exception e) {
            log.error("❌ OSS 上传异常 | key={}", objectKey, e);
            return "";
        }
    }
    /**
     * 上传图片文件到OSS
     * @param userId 用户ID
     * @param imageFile 图片文件
     * @param imageType 图片类型（png/svg）
     * @return OSS图片链接
     */
    public String uploadImage(String userId, File imageFile, String imageType) {
        if (imageFile == null || !imageFile.exists()) {
            log.warn("⚠️ 图片文件不存在 | userId={}", userId);
            return "";
        }

        // 构建OSS路径
        String uuid = UUID.randomUUID().toString().replaceAll("-", "");
        String objectKey = String.format("%s/%s/images/%s.%s",
                aliyunOssConfig.getFileHost(), userId, uuid, imageType);

        try (FileInputStream input = new FileInputStream(imageFile)) {
            ObjectMetadata meta = new ObjectMetadata();
            meta.setContentLength(imageFile.length());
            
            // 根据图片类型设置Content-Type
            if ("png".equalsIgnoreCase(imageType)) {
                meta.setContentType("image/png");
            } else if ("svg".equalsIgnoreCase(imageType)) {
                meta.setContentType("image/svg+xml");
            } else {
                meta.setContentType("image/" + imageType);
            }

            ossClient.putObject(aliyunOssConfig.getBucketName(), objectKey, input, meta);
            log.info("✅ 图片上传成功 | size={} bytes, key={}", imageFile.length(), objectKey);

            return aliyunOssConfig.getFileUrl() + "/" + objectKey;
        } catch (Exception e) {
            log.error("❌ 图片OSS上传异常 | key={}", objectKey, e);
            return "";
        }
    }

    /**
     * 上传图片字节数组到OSS
     * @param userId 用户ID
     * @param imageBytes 图片字节数组
     * @param imageType 图片类型（png/svg）
     * @return OSS图片链接
     */
    public String uploadImageBytes(String userId, byte[] imageBytes, String imageType) {
        if (imageBytes == null || imageBytes.length == 0) {
            log.warn("⚠️ 图片字节数组为空 | userId={}", userId);
            return "";
        }

        // 构建OSS路径
        String uuid = UUID.randomUUID().toString().replaceAll("-", "");
        String objectKey = String.format("%s/%s/images/%s.%s",
                aliyunOssConfig.getFileHost(), userId, uuid, imageType);

        try (ByteArrayInputStream input = new ByteArrayInputStream(imageBytes)) {
            ObjectMetadata meta = new ObjectMetadata();
            meta.setContentLength(imageBytes.length);
            
            // 根据图片类型设置Content-Type
            if ("png".equalsIgnoreCase(imageType)) {
                meta.setContentType("image/png");
            } else if ("svg".equalsIgnoreCase(imageType)) {
                meta.setContentType("image/svg+xml");
            } else {
                meta.setContentType("image/" + imageType);
            }

            ossClient.putObject(aliyunOssConfig.getBucketName(), objectKey, input, meta);
            log.info("✅ 图片字节数组上传成功 | size={} bytes, key={}", imageBytes.length, objectKey);

            return aliyunOssConfig.getFileUrl() + "/" + objectKey;
        } catch (Exception e) {
            log.error("❌ 图片字节数组OSS上传异常 | key={}", objectKey, e);
            return "";
        }
    }

    /**
     * 生成唯一的文件名，避免重复
     */
    private void generateFileName(Map<String, String> resultMap, Long userId, String originalFilename) {
        // 1. 获取文件后缀
        String suffix = originalFilename.substring(originalFilename.lastIndexOf("."));

        // 2. 生成UUID作为文件名主体
        String uuid = UUID.randomUUID().toString().replaceAll("-", "");

        // 3. 拼接完整文件名
        String filePath = aliyunOssConfig.getFileHost() + "/" + userId + "/" + uuid + suffix;
        String fileName = uuid + suffix;
        resultMap.put("id", uuid);
        resultMap.put("filePath", filePath);
        resultMap.put("fileName", fileName);
    }
}
