//package com.faers.agent.utils;
//
//import com.vladsch.flexmark.util.ast.Node;
//import com.vladsch.flexmark.ext.tables.TablesExtension;
//import com.vladsch.flexmark.html.HtmlRenderer;
//import com.vladsch.flexmark.parser.Parser;
//import com.vladsch.flexmark.util.data.MutableDataSet;
//import org.apache.pdfbox.pdmodel.PDDocument;
//import org.apache.pdfbox.pdmodel.PDPage;
//import org.apache.pdfbox.pdmodel.PDPageContentStream;
//import org.apache.pdfbox.pdmodel.font.PDType0Font;
//import org.apache.pdfbox.pdmodel.font.PDType1Font;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.stereotype.Component;
//
//import java.io.File;
//import java.io.FileOutputStream;
//import java.io.IOException;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.nio.file.Paths;
//import java.time.LocalDateTime;
//import java.time.format.DateTimeFormatter;
//import java.util.Arrays;
//
///**
// * Markdown转PDF文档工具类
// * 支持标题、列表、表格、代码块、图表等元素的转换
// */
//@Component
//public class MarkdownToPdfConverter {
//    private static final Logger log = LoggerFactory.getLogger(MarkdownToPdfConverter.class);
//
//    // Flexmark解析器配置
//    private final Parser parser;
//    private final HtmlRenderer renderer;
//
//    public MarkdownToPdfConverter() {
//        // 配置Flexmark解析器
//        MutableDataSet options = new MutableDataSet();
//        options.set(Parser.EXTENSIONS, Arrays.asList(
//                TablesExtension.create()
//        ));
//        options.set(HtmlRenderer.HARD_BREAK, "<br/>");
//
//        // 初始化解析器和渲染器
//        parser = Parser.builder(options).build();
//        renderer = HtmlRenderer.builder(options).build();
//    }
//
//    /**
//     * 将Markdown内容转换为PDF文档
//     * @param markdownContent Markdown内容
//     * @param topicName 主题名称
//     * @param traceId 追踪ID
//     * @return 生成的PDF文档路径
//     * @throws IOException IO异常
//     */
//    public String convert(String markdownContent, String topicName, String traceId) throws IOException {
//        // 生成文件名：使用主题名称和时间戳
//        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
//        String safeTopicName = topicName != null && !topicName.isEmpty()
//            ? topicName.replaceAll("[\\/:*?\"<>|]", "_").substring(0, Math.min(50, topicName.length()))
//            : "项目申报书";
//        String fileName = safeTopicName + "_" + timestamp + ".pdf";
//
//        // 保存到项目根目录的reports文件夹
//        Path basePath = Paths.get(".").toAbsolutePath().normalize();
//        Path reportsPath = basePath.resolve("reports");
//        Files.createDirectories(reportsPath);
//        Path pdfPath = reportsPath.resolve(fileName);
//
//        // 创建PDF文档
//        try (PDDocument document = new PDDocument()) {
//            // 直接从Markdown内容提取纯文本，确保移除所有HTML标签和Markdown标记
//            String plainText = extractPlainText(markdownContent);
//
//            // 按行分割文本
//            String[] lines = plainText.split("\\n");
//
//            // 设置字体和大小
//            float fontSize = 12;
//            float leading = 14.5f;
//
//            // 起始位置
//            float startX = 50;
//            float startY = 0;
//            float currentY = 0;
//
//            // 添加第一页
//            PDPage page = new PDPage();
//            document.addPage(page);
//            startY = page.getMediaBox().getHeight() - 50;
//            currentY = startY;
//
//            PDPageContentStream contentStream = new PDPageContentStream(document, page);
//
//            // 尝试加载系统中的中文字体，使用PDType0Font支持中文
//            PDType0Font font = null;
//            boolean useCustomFont = false;
//            try {
//                // 尝试加载Windows系统中的宋体
//                font = PDType0Font.load(document, new File("C:\\WINDOWS\\FONTS\\arial.ttf"));
//                useCustomFont = true;
//            } catch (Exception e) {
//                // 如果加载失败，回退到使用内置字体
//                log.warn("Failed to load custom font, using default Helvetica font: {}", e.getMessage());
//            }
//
//            if (useCustomFont) {
//                contentStream.setFont(font, fontSize);
//            } else {
//                // 使用内置字体
//                contentStream.setFont(PDType1Font.HELVETICA, fontSize);
//            }
//            contentStream.setLeading(leading);
//
//            try {
//                for (String line : lines) {
//                    // 跳过空行
//                    if (line.trim().isEmpty()) {
//                        continue;
//                    }
//
//                    // 检查是否需要新页面
//                    if (currentY < 50) {
//                        // 关闭当前内容流
//                        contentStream.close();
//
//                        // 添加新页面
//                        page = new PDPage();
//                        document.addPage(page);
//
//                        // 创建新的内容流
//                        contentStream = new PDPageContentStream(document, page);
//                        contentStream.setFont(font, fontSize);
//                        contentStream.setLeading(leading);
//
//                        // 重置起始位置
//                        currentY = page.getMediaBox().getHeight() - 50;
//                    }
//
//                    // 写入行，始终过滤中文字符，确保转换成功
//                    contentStream.beginText();
//                    contentStream.newLineAtOffset(startX, currentY);
//                    String textToWrite = filterUnsupportedCharacters(line);
//                    contentStream.showText(textToWrite);
//                    contentStream.endText();
//                    currentY -= leading;
//                }
//            } finally {
//                // 确保最后一个内容流被关闭
//                if (contentStream != null) {
//                    contentStream.close();
//                }
//            }
//
//            // 保存文档
//            document.save(pdfPath.toFile());
//            log.info("PDF document created successfully: {}", pdfPath.toAbsolutePath());
//
//        } catch (Exception e) {
//            log.error("Error converting Markdown to PDF: {}", e.getMessage(), e);
//            throw e;
//        }
//
//        return pdfPath.toAbsolutePath().toString();
//    }
//
//    /**
//     * 过滤不支持的字符，只保留Helvetica字体支持的字符
//     * @param text 原始文本
//     * @return 过滤后的安全文本
//     */
//    private String filterUnsupportedCharacters(String text) {
//        if (text == null) {
//            return "";
//        }
//
//        StringBuilder safeText = new StringBuilder();
//        for (char c : text.toCharArray()) {
//            // 保留ASCII字符和常见符号
//            if (c <= 127) {
//                safeText.append(c);
//            } else {
//                // 对于中文字符，使用"[CH]"代替，使用纯ASCII字符避免编码问题
//                safeText.append("[CH]");
//            }
//        }
//        return safeText.toString();
//    }
//
//    /**
//     * 提取Markdown文本内容，确保移除所有HTML标签和Markdown标记
//     * @param markdownContent Markdown内容
//     * @return 纯文本内容
//     */
//    private String extractPlainText(String markdownContent) {
//        // 首先移除所有HTML标签
//        String noHtml = markdownContent.replaceAll("<[^>]*>", "");
//
//        // 然后移除Markdown标记
//        return noHtml
//            .replaceAll("#+\s*", "") // 移除标题标记
//            .replaceAll("\\*\\*([^*]+)\\*\\*", "$1") // 移除粗体标记
//            .replaceAll("\\*([^*]+)\\*", "$1") // 移除斜体标记
//            .replaceAll("`([^`]+)`", "$1") // 移除行内代码标记
//            .replaceAll("(?m)^[\\*+\\-] ", "") // 移除无序列表标记，使用(?m)启用多行模式
//            .replaceAll("(?m)^\\d+\\. ", "") // 移除有序列表标记，使用(?m)启用多行模式
//            .replaceAll("> ", "") // 移除引用标记
//            .replaceAll("```[\\s\\S]*?```", "[代码块]") // 替换代码块
//            .replaceAll("!\\[.*?\\]\\(.*?\\)", "[图片]"); // 替换图片
//    }
//}