package com.faers.agent.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

@Slf4j
public class MermaidToImageUtils {

    // Mermaid.ink API 地址（支持 SVG 和 PNG 格式）
    private static final String MERMAID_INK_SVG_API = "https://mermaid.ink/svg/";
    private static final String MERMAID_INK_PNG_API = "https://mermaid.ink/img/";
    
    // 备用 API
    private static final String KROKI_API = "https://kroki.io/mermaid/";
    private static final String MERMAID_LIVE_API = "https://mermaid-js.github.io/mermaid-live-editor/view#";
    
    // 连接超时时间（毫秒）
    private static final int CONNECT_TIMEOUT = 30000;
    private static final int READ_TIMEOUT = 60000;
    
    // 重试配置
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final int RETRY_DELAY_MS = 2000;
    
    // 图片质量配置（用于 PNG）
    private static final String HIGH_QUALITY_SUFFIX = "?scale=2"; // 2倍分辨率

    /**
     * 预处理和验证 Mermaid 代码
     * 
     * @param mermaidCode 原始 Mermaid 代码
     * @return 处理后的代码
     */
    private static String preprocessMermaidCode(String mermaidCode) {
        if (mermaidCode == null || mermaidCode.trim().isEmpty()) {
            return mermaidCode;
        }
        
        // 去除首尾空白
        String processed = mermaidCode.trim();
        
        // 规范化换行符
        processed = processed.replace("\r\n", "\n").replace("\r", "\n");
        
        // 移除可能导致问题的特殊字符
        processed = processed.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "");
        
        // 确保代码不为空
        if (processed.isEmpty()) {
            log.warn("预处理后的 Mermaid 代码为空");
            return null;
        }
        
        log.debug("Mermaid 代码预处理完成，长度: {} -> {}", mermaidCode.length(), processed.length());
        return processed;
    }
    
    /**
     * 验证 Mermaid 代码的基本语法
     * 
     * @param mermaidCode Mermaid 代码
     * @return 是否有效
     */
    private static boolean validateMermaidCode(String mermaidCode) {
        if (mermaidCode == null || mermaidCode.trim().isEmpty()) {
            log.warn("Mermaid 代码为空");
            return false;
        }
        
        // 检查是否包含基本的图表类型关键字
        String[] validKeywords = {
            "graph", "flowchart", "sequenceDiagram", "classDiagram", 
            "stateDiagram", "erDiagram", "gantt", "pie", "journey",
            "gitGraph", "mindmap", "timeline", "quadrantChart"
        };
        
        String lowerCode = mermaidCode.toLowerCase();
        boolean hasValidKeyword = false;
        for (String keyword : validKeywords) {
            if (lowerCode.contains(keyword.toLowerCase())) {
                hasValidKeyword = true;
                break;
            }
        }
        
        if (!hasValidKeyword) {
            log.warn("Mermaid 代码可能无效，未找到有效的图表类型关键字");
        }
        
        return true; // 即使没有关键字也返回 true，因为可能是新语法
    }

    /**
     * 将 Mermaid 代码编码为 Base64 格式（使用 pako 压缩格式）
     * 
     * @param mermaidCode Mermaid 代码
     * @return Base64 编码后的字符串
     */
    private static String encodeMermaidCode(String mermaidCode) {
        try {
            // 方式1：简单 Base64 编码（兼容性最好）
            String encoded = Base64.getEncoder().encodeToString(mermaidCode.getBytes(StandardCharsets.UTF_8));
            // 使 URL 安全
            return encoded.replace("+", "-").replace("/", "_").replace("=", "");
        } catch (Exception e) {
            log.error("Mermaid 代码编码失败", e);
            return null;
        }
    }
    
    /**
     * 使用 Deflate 压缩并编码（兼容 pako 格式）
     * 
     * @param mermaidCode Mermaid 代码
     * @return 压缩并编码后的字符串
     */
    private static String compressAndEncode(String mermaidCode) {
        try {
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            DeflaterOutputStream deflaterStream = new DeflaterOutputStream(byteStream, new Deflater(Deflater.DEFAULT_COMPRESSION, true));
            deflaterStream.write(mermaidCode.getBytes(StandardCharsets.UTF_8));
            deflaterStream.close();
            
            byte[] compressed = byteStream.toByteArray();
            String encoded = Base64.getEncoder().encodeToString(compressed);
            
            // 使 URL 安全
            return encoded.replace("+", "-").replace("/", "_").replace("=", "");
        } catch (Exception e) {
            log.error("Mermaid 代码压缩编码失败", e);
            return null;
        }
    }
    
    /**
     * 下载图片到本地（带重试机制）
     * 
     * @param imageUrl 图片 URL
     * @param outputFile 输出文件路径
     * @return 是否成功
     */
    private static boolean downloadImage(String imageUrl, Path outputFile) {
        return downloadImageWithRetry(imageUrl, outputFile, MAX_RETRY_ATTEMPTS);
    }
    
    /**
     * 下载图片到本地（带重试机制）
     * 
     * @param imageUrl 图片 URL
     * @param outputFile 输出文件路径
     * @param retryAttempts 剩余重试次数
     * @return 是否成功
     */
    private static boolean downloadImageWithRetry(String imageUrl, Path outputFile, int retryAttempts) {
        try {
            URL url = new URL(imageUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            connection.setRequestProperty("Accept", "image/png,image/svg+xml,image/*,*/*");
            connection.setRequestProperty("Accept-Encoding", "gzip, deflate");
            connection.setRequestProperty("Connection", "keep-alive");
            
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (InputStream inputStream = connection.getInputStream();
                     FileOutputStream outputStream = new FileOutputStream(outputFile.toFile())) {
                    
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    long totalBytes = 0;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                        totalBytes += bytesRead;
                    }
                    
                    // 验证文件大小
                    if (totalBytes < 100) {
                        log.warn("下载的文件太小 ({} bytes)，可能是错误页面", totalBytes);
                        if (retryAttempts > 0) {
                            log.info("重试下载... 剩余尝试次数: {}", retryAttempts);
                            Thread.sleep(RETRY_DELAY_MS);
                            return downloadImageWithRetry(imageUrl, outputFile, retryAttempts - 1);
                        }
                        return false;
                    }
                    
                    log.info("图片下载成功: {} (大小: {} bytes)", outputFile, totalBytes);
                    return true;
                }
            } else if (responseCode == HttpURLConnection.HTTP_GATEWAY_TIMEOUT || 
                       responseCode == HttpURLConnection.HTTP_UNAVAILABLE ||
                       responseCode == 429) { // Too Many Requests
                log.warn("HTTP 响应码: {}，服务暂时不可用", responseCode);
                if (retryAttempts > 0) {
                    log.info("等待后重试... 剩余尝试次数: {}", retryAttempts);
                    Thread.sleep(RETRY_DELAY_MS * (MAX_RETRY_ATTEMPTS - retryAttempts + 1)); // 指数退避
                    return downloadImageWithRetry(imageUrl, outputFile, retryAttempts - 1);
                }
                return false;
            } else {
                log.error("图片下载失败，HTTP 响应码: {}", responseCode);
                
                // 尝试读取错误信息
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getErrorStream() != null ? 
                                connection.getErrorStream() : connection.getInputStream()))) {
                    String line;
                    StringBuilder errorMsg = new StringBuilder();
                    while ((line = reader.readLine()) != null) {
                        errorMsg.append(line).append("\n");
                    }
                    if (errorMsg.length() > 0) {
                        log.error("错误详情: {}", errorMsg.toString().substring(0, Math.min(500, errorMsg.length())));
                    }
                } catch (Exception e) {
                    // 忽略读取错误信息时的异常
                }
                
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("下载图片被中断: {}", imageUrl);
            return false;
        } catch (Exception e) {
            log.error("下载图片异常: {}", imageUrl, e);
            if (retryAttempts > 0 && (e instanceof java.net.SocketTimeoutException || 
                                      e instanceof java.net.ConnectException)) {
                log.info("网络异常，重试... 剩余尝试次数: {}", retryAttempts);
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                    return downloadImageWithRetry(imageUrl, outputFile, retryAttempts - 1);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            return false;
        }
    }

    /**
     * 渲染 Mermaid 代码为图片（PNG 格式）
     *
     * @param mermaidCode Mermaid 代码
     * @param outputPath 输出路径
     * @param outputFileName 输出文件名（建议以 .png 结尾）
     * @return 生成的图片文件，失败返回 null
     */
    public static File renderMermaidToImage(String mermaidCode, String outputPath, String outputFileName) {
        return renderMermaidToImage(mermaidCode, outputPath, outputFileName, "png");
    }

    /**
     * 渲染 Mermaid 代码为图片（支持 PNG 或 SVG 格式）
     *
     * @param mermaidCode Mermaid 代码
     * @param outputPath 输出路径
     * @param outputFileName 输出文件名
     * @param format 输出格式（"png" 或 "svg"）
     * @return 生成的图片文件，失败返回 null
     */
    public static File renderMermaidToImage(String mermaidCode, String outputPath, String outputFileName, String format) {
        if (mermaidCode == null || mermaidCode.trim().isEmpty()) {
            log.error("Mermaid 代码为空，无法生成图片");
            return null;
        }

        try {
            // 预处理和验证 Mermaid 代码
            String processedCode = preprocessMermaidCode(mermaidCode);
            if (processedCode == null || !validateMermaidCode(processedCode)) {
                log.error("Mermaid 代码预处理或验证失败");
                return null;
            }
            
            // 创建输出目录
            Path outputDir = Paths.get(outputPath);
            if (!Files.exists(outputDir)) {
                Files.createDirectories(outputDir);
            }

            Path outputFile = outputDir.resolve(outputFileName);

            // 编码 Mermaid 代码
            String encoded = encodeMermaidCode(processedCode);
            if (encoded == null) {
                log.error("Mermaid 代码编码失败");
                return null;
            }

            // 构建 API URL（PNG 格式使用高清模式）
            String apiUrl;
            boolean isPng = "png".equalsIgnoreCase(format);
            if ("svg".equalsIgnoreCase(format)) {
                apiUrl = MERMAID_INK_SVG_API + encoded;
                log.info("使用 SVG 格式（矢量图，无损缩放）");
            } else {
                // PNG 格式，尝试使用高清模式
                apiUrl = MERMAID_INK_PNG_API + encoded;
                log.info("使用 PNG 格式（高清模式）");
            }

            log.info("使用 Mermaid.ink API 生成图片: {}", apiUrl.substring(0, Math.min(100, apiUrl.length())) + "...");

            // 下载图片
            boolean success = downloadImage(apiUrl, outputFile);
            
            if (!success) {
                // 如果主 API 失败，尝试使用备用 API（Kroki）
                log.warn("Mermaid.ink API 失败，尝试使用备用 API（Kroki）");
                return renderMermaidWithKroki(processedCode, outputPath, outputFileName, format);
            }

            if (Files.exists(outputFile) && Files.size(outputFile) > 0) {
                log.info("✓ Mermaid 图片生成成功: {} (大小: {} bytes)", outputFile, Files.size(outputFile));
                return outputFile.toFile();
            } else {
                log.error("图片文件未生成或为空: {}", outputFile);
                // 尝试备用 API
                log.warn("尝试使用备用 API（Kroki）");
                return renderMermaidWithKroki(processedCode, outputPath, outputFileName, format);
            }

        } catch (Exception e) {
            log.error("Mermaid 图片生成异常", e);
            return null;
        }
    }

    /**
     * 使用 Kroki API 渲染 Mermaid 图表（备用方案）
     *
     * @param mermaidCode Mermaid 代码（已预处理）
     * @param outputPath 输出路径
     * @param outputFileName 输出文件名
     * @param format 输出格式（"png" 或 "svg"）
     * @return 生成的图片文件，失败返回 null
     */
    private static File renderMermaidWithKroki(String mermaidCode, String outputPath, String outputFileName, String format) {
        try {
            Path outputDir = Paths.get(outputPath);
            Path outputFile = outputDir.resolve(outputFileName);

            // Kroki API 使用压缩编码
            String compressed = compressAndEncode(mermaidCode);
            if (compressed == null) {
                log.error("Kroki API: Mermaid 代码压缩编码失败");
                return null;
            }

            // 构建 Kroki API URL
            String formatPath = "svg".equalsIgnoreCase(format) ? "svg" : "png";
            String apiUrl = KROKI_API + formatPath + "/" + compressed;

            log.info("使用 Kroki API 生成图片: {}", apiUrl.substring(0, Math.min(100, apiUrl.length())) + "...");

            // 下载图片
            boolean success = downloadImage(apiUrl, outputFile);

            if (success && Files.exists(outputFile) && Files.size(outputFile) > 0) {
                log.info("✓ Kroki API: Mermaid 图片生成成功: {} (大小: {} bytes)", outputFile, Files.size(outputFile));
                return outputFile.toFile();
            } else {
                log.error("Kroki API: 图片文件未生成或为空: {}", outputFile);
                // 尝试使用简单编码方式
                return renderMermaidWithKrokiSimple(mermaidCode, outputPath, outputFileName, format);
            }

        } catch (Exception e) {
            log.error("Kroki API: Mermaid 图片生成异常", e);
            // 尝试使用简单编码方式
            return renderMermaidWithKrokiSimple(mermaidCode, outputPath, outputFileName, format);
        }
    }
    
    /**
     * 使用 Kroki API 渲染 Mermaid 图表（使用简单编码，最后的备用方案）
     *
     * @param mermaidCode Mermaid 代码（已预处理）
     * @param outputPath 输出路径
     * @param outputFileName 输出文件名
     * @param format 输出格式（"png" 或 "svg"）
     * @return 生成的图片文件，失败返回 null
     */
    private static File renderMermaidWithKrokiSimple(String mermaidCode, String outputPath, String outputFileName, String format) {
        try {
            Path outputDir = Paths.get(outputPath);
            Path outputFile = outputDir.resolve(outputFileName);

            // 使用简单 Base64 编码
            String encoded = encodeMermaidCode(mermaidCode);
            if (encoded == null) {
                log.error("Kroki API (Simple): Mermaid 代码编码失败");
                return null;
            }

            // 构建 Kroki API URL（使用 POST 方式可能更稳定，但这里用 GET）
            String formatPath = "svg".equalsIgnoreCase(format) ? "svg" : "png";
            String apiUrl = KROKI_API + formatPath + "/" + encoded;

            log.info("使用 Kroki API (Simple Encoding) 生成图片...");

            // 下载图片
            boolean success = downloadImage(apiUrl, outputFile);

            if (success && Files.exists(outputFile) && Files.size(outputFile) > 0) {
                log.info("✓ Kroki API (Simple): Mermaid 图片生成成功: {} (大小: {} bytes)", 
                         outputFile, Files.size(outputFile));
                return outputFile.toFile();
            } else {
                log.error("✗ Kroki API (Simple): 所有备用方案均失败，无法生成图片");
                return null;
            }

        } catch (Exception e) {
            log.error("Kroki API (Simple): Mermaid 图片生成异常", e);
            return null;
        }
    }

    /**
     * 渲染 Mermaid 代码为 SVG 格式（矢量图，支持无损缩放）
     * 推荐使用 SVG 格式以获得最佳清晰度
     *
     * @param mermaidCode Mermaid 代码
     * @param outputPath 输出路径
     * @param outputFileName 输出文件名（建议以 .svg 结尾）
     * @return 生成的 SVG 文件，失败返回 null
     */
    public static File renderMermaidToSVG(String mermaidCode, String outputPath, String outputFileName) {
        log.info("推荐：使用 SVG 格式可获得矢量图，支持无损缩放，清晰度最高");
        return renderMermaidToImage(mermaidCode, outputPath, outputFileName, "svg");
    }
    
    /**
     * 渲染 Mermaid 代码为 PNG 格式（高清模式）
     *
     * @param mermaidCode Mermaid 代码
     * @param outputPath 输出路径
     * @param outputFileName 输出文件名（建议以 .png 结尾）
     * @return 生成的 PNG 文件，失败返回 null
     */
    public static File renderMermaidToPNG(String mermaidCode, String outputPath, String outputFileName) {
        return renderMermaidToImage(mermaidCode, outputPath, outputFileName, "png");
    }
    
    /**
     * 渲染 Mermaid 代码为高清图片（优先使用 SVG，确保最佳质量）
     * 
     * @param mermaidCode Mermaid 代码
     * @param outputPath 输出路径
     * @param outputFileName 输出文件名
     * @return 生成的图片文件，失败返回 null
     */
    public static File renderMermaidToHighQuality(String mermaidCode, String outputPath, String outputFileName) {
        log.info("高清模式：优先使用 SVG 格式以获得最佳清晰度");
        
        // 如果文件名不包含扩展名，自动添加 .svg
        if (!outputFileName.contains(".")) {
            outputFileName += ".svg";
        } else if (outputFileName.endsWith(".png")) {
            // 如果指定了 PNG，改为 SVG
            outputFileName = outputFileName.substring(0, outputFileName.length() - 4) + ".svg";
            log.info("已将输出格式从 PNG 改为 SVG 以提升清晰度");
        }
        
        return renderMermaidToSVG(mermaidCode, outputPath, outputFileName);
    }
    
    /**
     * 兼容旧版本的超高清渲染方法（现在使用 SVG 矢量图）
     *
     * @param mermaidCode Mermaid 代码
     * @param outputPath 输出路径
     * @param outputFileName 输出文件名
     * @return 生成的图片文件
     */
    @Deprecated
    public static File renderMermaidToImageUHD(String mermaidCode, String outputPath, String outputFileName) {
        // 使用 SVG 格式代替超高清 PNG，SVG 支持无损缩放
        log.info("renderMermaidToImageUHD 已弃用，已自动切换到 SVG 格式以获得更高清晰度");
        return renderMermaidToHighQuality(mermaidCode, outputPath, outputFileName);
    }

    public static String extractMermaidCode(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return "";
        }

        int startIdx = markdown.indexOf("```mermaid");
        if (startIdx == -1) {
            return "";
        }

        startIdx = markdown.indexOf("\n", startIdx);
        if (startIdx == -1) {
            return "";
        }
        startIdx++;

        int endIdx = markdown.indexOf("```", startIdx);
        if (endIdx == -1) {
            return "";
        }

        return markdown.substring(startIdx, endIdx).trim();
    }
}
