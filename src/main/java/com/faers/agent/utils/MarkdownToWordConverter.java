package com.faers.agent.utils;

import com.vladsch.flexmark.ast.*;
import com.vladsch.flexmark.ext.tables.*;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;
import org.apache.poi.xwpf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

/**
 * Markdown转Word文档工具类
 * 支持标题、列表、表格、代码块、图表等元素的转换
 */
@Component
public class MarkdownToWordConverter {
    private static final Logger log = LoggerFactory.getLogger(MarkdownToWordConverter.class);
    
    // Flexmark解析器配置
    private final Parser parser;
    private final HtmlRenderer renderer;

    public MarkdownToWordConverter() {
        // 配置Flexmark解析器
        MutableDataSet options = new MutableDataSet();
        options.set(Parser.EXTENSIONS, Arrays.asList(
                TablesExtension.create()
        ));
        options.set(HtmlRenderer.HARD_BREAK, "<br/>");
        
        // 初始化解析器和渲染器
        parser = Parser.builder(options).build();
        renderer = HtmlRenderer.builder(options).build();
    }

    /**
     * 将Markdown内容转换为Word文档
     * @param markdownContent Markdown内容
     * @param topicName 主题名称
     * @param traceId 追踪ID
     * @return 生成的Word文档路径
     * @throws IOException IO异常
     */
    public String convert(String markdownContent, String topicName, String traceId) throws IOException {
        // 生成文件名：使用主题名称和时间戳
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String safeTopicName = topicName != null && !topicName.isEmpty() 
            ? topicName.replaceAll("[\\/:*?\"<>|]", "_").substring(0, Math.min(50, topicName.length()))
            : "项目申报书";
        String fileName = safeTopicName + "_" + timestamp + ".docx";
        
        // 保存到项目根目录的reports文件夹
        Path wordPath = Paths.get("reports", fileName);
        Files.createDirectories(wordPath.getParent());
        
        // 创建Word文档
        try (XWPFDocument document = new XWPFDocument();
             FileOutputStream out = new FileOutputStream(wordPath.toFile())) {
            
            // 解析Markdown文档
            Node documentNode = parser.parse(markdownContent);
            
            // 处理文档内容
            processNode(documentNode, document, null);
            
            // 保存文档
            document.write(out);
            log.info("Word document created successfully: {}", wordPath.toAbsolutePath());
            
        } catch (Exception e) {
            log.error("Error converting Markdown to Word: {}", e.getMessage(), e);
            throw e;
        }
        
        return wordPath.toAbsolutePath().toString();
    }

    /**
     * 递归处理Markdown节点
     * @param node 当前节点
     * @param document Word文档
     * @param parentParagraph 父段落（用于行内元素）
     */
    private void processNode(Node node, XWPFDocument document, XWPFParagraph parentParagraph) {
        if (node instanceof Heading heading) {
            // 处理标题
            processHeading(heading, document);
        } else if (node instanceof BulletList bulletList) {
            // 处理无序列表
            processBulletList(bulletList, document);
        } else if (node instanceof OrderedList orderedList) {
            // 处理有序列表
            processOrderedList(orderedList, document);
        } else if (node instanceof TableBlock tableBlock) {
            // 处理表格
            processTable(tableBlock, document);
        } else if (node instanceof Paragraph paragraph) {
            // 处理段落
            processParagraph(paragraph, document);
        } else if (node instanceof FencedCodeBlock codeBlock) {
            // 处理代码块
            processCodeBlock(codeBlock, document);
        } else if (node instanceof Code code) {
            // 处理行内代码
            processInlineCode(code, document);
        } else if (node instanceof BlockQuote blockQuote) {
            // 处理引用
            processBlockQuote(blockQuote, document);
        } else if (node instanceof Image image) {
            // 处理图片
            processImage(image, document);
        }
        
        // 处理子节点
        Node child = node.getFirstChild();
        while (child != null) {
            Node next = child.getNext();
            processNode(child, document, null);
            child = next;
        }
    }

    /**
     * 处理标题
     * @param heading 标题节点
     * @param document Word文档
     */
    private void processHeading(Heading heading, XWPFDocument document) {
        int level = heading.getLevel();
        String text = getNodeText(heading);
        
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        
        XWPFParagraph paragraph = document.createParagraph();
        XWPFRun run = paragraph.createRun();
        run.setText(text);
        
        // 根据标题级别设置不同的字体大小和粗细
        switch (level) {
            case 1:
                run.setFontSize(24);
                run.setBold(true);
                paragraph.setAlignment(ParagraphAlignment.CENTER);
                break;
            case 2:
                run.setFontSize(20);
                run.setBold(true);
                paragraph.setAlignment(ParagraphAlignment.LEFT);
                break;
            case 3:
                run.setFontSize(16);
                run.setBold(true);
                paragraph.setAlignment(ParagraphAlignment.LEFT);
                break;
            case 4:
                run.setFontSize(14);
                run.setBold(true);
                paragraph.setAlignment(ParagraphAlignment.LEFT);
                break;
            default:
                run.setFontSize(12);
                run.setBold(true);
                paragraph.setAlignment(ParagraphAlignment.LEFT);
        }
        
        // 设置段落间距
        setParagraphSpacing(paragraph, 120, 60);
    }

    /**
     * 处理段落
     * @param paragraphNode 段落节点
     * @param document Word文档
     */
    private void processParagraph(Paragraph paragraphNode, XWPFDocument document) {
        String text = getNodeText(paragraphNode);
        
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        
        XWPFParagraph paragraph = document.createParagraph();
        XWPFRun run = paragraph.createRun();
        run.setText(text);
        run.setFontSize(12);
        
        // 设置段落间距
        setParagraphSpacing(paragraph, 60, 60);
    }

    /**
     * 处理无序列表
     * @param bulletList 无序列表节点
     * @param document Word文档
     */
    private void processBulletList(BulletList bulletList, XWPFDocument document) {
        Node child = bulletList.getFirstChild();
        while (child != null) {
            if (child instanceof ListItem listItem) {
                processListItem(listItem, document, "• ");
            }
            child = child.getNext();
        }
    }

    /**
     * 处理有序列表
     * @param orderedList 有序列表节点
     * @param document Word文档
     */
    private void processOrderedList(OrderedList orderedList, XWPFDocument document) {
        Node child = orderedList.getFirstChild();
        int index = 1;
        while (child != null) {
            if (child instanceof ListItem listItem) {
                processListItem(listItem, document, index + ". ");
                index++;
            }
            child = child.getNext();
        }
    }

    /**
     * 处理列表项
     * @param listItem 列表项节点
     * @param document Word文档
     * @param bulletSymbol 列表符号
     */
    private void processListItem(ListItem listItem, XWPFDocument document, String bulletSymbol) {
        String text = getNodeText(listItem);
        
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setIndentationFirstLine(0);
        
        XWPFRun run = paragraph.createRun();
        run.setText(bulletSymbol);
        run.setFontSize(12);
        
        run = paragraph.createRun();
        run.setText(text);
        run.setFontSize(12);
        
        // 设置段落间距
        setParagraphSpacing(paragraph, 30, 30);
    }

    /**
     * 处理表格
     * @param tableBlock 表格节点
     * @param document Word文档
     */
    private void processTable(TableBlock tableBlock, XWPFDocument document) {
        try {
            // 创建Word表格
            XWPFTable table = document.createTable();
            table.setWidth("100%");
            
            // 获取表格行节点
            Node child = tableBlock.getFirstChild();
            boolean isHeader = true;
            
            while (child != null) {
                if (child instanceof com.vladsch.flexmark.ext.tables.TableRow tableRowNode) {
                    // 如果是表头，创建表头行
                    // 如果是内容行，创建新行
                    XWPFTableRow row = isHeader ? table.getRow(0) : table.createRow();
                    
                    // 处理表格行
                    processTableRow(tableRowNode, row, isHeader);
                    isHeader = false;
                }
                child = child.getNext();
            }
            
            // 设置表格间距
            XWPFParagraph paragraph = document.createParagraph();
            setParagraphSpacing(paragraph, 60, 60);
        } catch (Exception e) {
            log.warn("Error processing table, using simplified representation: {}", e.getMessage());
            // 如果表格处理失败，使用简化表示
            XWPFParagraph paragraph = document.createParagraph();
            XWPFRun run = paragraph.createRun();
            run.setText("[表格内容 - 详细表格请查看原始Markdown]");
            run.setFontSize(12);
            run.setItalic(true);
            run.setColor("666666");
            setParagraphSpacing(paragraph, 60, 60);
        }
    }

    /**
     * 处理表格行
     * @param tableRow 表格行节点
     * @param row Word表格行
     * @param isHeader 是否为表头
     */
    // 简化表格行处理，不再使用
    private void processTableRow(Node tableRow, XWPFTableRow row, boolean isHeader) {
        Node cellNode = tableRow.getFirstChild();
        int cellIndex = 0;
        
        while (cellNode != null) {
            if (cellNode instanceof com.vladsch.flexmark.ext.tables.TableCell tableCell) {
                // 确保表格行有足够的单元格
                while (cellIndex >= row.getTableCells().size()) {
                    row.createCell();
                }
                
                XWPFTableCell cell = row.getTableCells().get(cellIndex);
                XWPFParagraph paragraph = cell.getParagraphs().get(0);
                XWPFRun run = paragraph.createRun();
                
                // 设置单元格内容
                String text = getNodeText(tableCell);
                run.setText(text != null ? text : "");
                run.setFontSize(11);
                
                // 设置表头样式
                if (isHeader) {
                    run.setBold(true);
                    cell.setColor("E6E6E6");
                }
                
                cellIndex++;
            }
            cellNode = cellNode.getNext();
        }
    }

    /**
     * 处理代码块
     * @param codeBlock 代码块节点
     * @param document Word文档
     */
    private void processCodeBlock(FencedCodeBlock codeBlock, XWPFDocument document) {
        String code = getNodeText(codeBlock);
        String language = codeBlock.getInfo() != null ? codeBlock.getInfo().toString() : null;
        
        if (code == null || code.trim().isEmpty()) {
            return;
        }
        
        // 创建代码块标题
        XWPFParagraph titleParagraph = document.createParagraph();
        XWPFRun titleRun = titleParagraph.createRun();
        
        // 根据代码类型设置不同的标题
        if (language != null && language.contains("mermaid")) {
            titleRun.setText("🔄 Mermaid图表代码");
        } else if (language != null && language.contains("plantuml")) {
            titleRun.setText("📊 PlantUML流程图代码");
        } else {
            titleRun.setText("💻 代码块");
            if (language != null) {
                titleRun.setText(" (" + language + ")");
            }
        }
        
        titleRun.setFontSize(12);
        titleRun.setBold(true);
        titleRun.setColor("333333");
        setParagraphSpacing(titleParagraph, 60, 30);
        
        // 创建代码块内容段落
        XWPFParagraph codeParagraph = document.createParagraph();
        codeParagraph.setAlignment(ParagraphAlignment.LEFT);
        codeParagraph.setIndentationLeft(720); // 缩进
        
        XWPFRun codeRun = codeParagraph.createRun();
        
        // 设置代码样式
        codeRun.setText(code);
        codeRun.setFontSize(10);
        codeRun.setFontFamily("Courier New");
        codeRun.setColor("000000");
        
        // 设置代码块间距
        setParagraphSpacing(codeParagraph, 0, 30);
        
        // 创建图表说明段落
        XWPFParagraph noteParagraph = document.createParagraph();
        XWPFRun noteRun = noteParagraph.createRun();
        
        if (language != null && (language.contains("mermaid") || language.contains("plantuml"))) {
            noteRun.setText("📌 说明：此代码可通过Mermaid/PlantUML编辑器渲染为图表。");
            noteRun.setFontSize(10);
            noteRun.setColor("666666");
            noteRun.setItalic(true);
            
            // 添加使用提示
            noteRun.addBreak();
            noteRun.setText("🔗 推荐工具：https://mermaid.live/ (Mermaid) 或 https://www.plantuml.com/plantuml/uml (PlantUML)");
        } else {
            noteRun.setText("📌 代码块可复制到开发工具中查看完整格式");
            noteRun.setFontSize(10);
            noteRun.setColor("666666");
            noteRun.setItalic(true);
        }
        
        setParagraphSpacing(noteParagraph, 0, 60);
    }

    /**
     * 处理行内代码
     * @param code 行内代码节点
     * @param document Word文档
     */
    private void processInlineCode(Code code, XWPFDocument document) {
        String text = getNodeText(code);
        
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        
        XWPFParagraph paragraph = document.createParagraph();
        XWPFRun run = paragraph.createRun();
        run.setText(text);
        run.setFontSize(12);
        run.setFontFamily("Courier New");
        run.setColor("0066CC");
    }

    /**
     * 处理引用
     * @param blockQuote 引用节点
     * @param document Word文档
     */
    private void processBlockQuote(BlockQuote blockQuote, XWPFDocument document) {
        String text = getNodeText(blockQuote);
        
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        
        XWPFParagraph paragraph = document.createParagraph();
        XWPFRun run = paragraph.createRun();
        run.setText("\"" + text + "\"");
        run.setFontSize(12);
        run.setColor("666666");
        run.setItalic(true);
        
        // 设置段落缩进
        paragraph.setIndentationLeft(720);
        
        // 设置段落间距
        setParagraphSpacing(paragraph, 60, 60);
    }

    /**
     * 处理图片
     * @param image 图片节点
     * @param document Word文档
     */
    private void processImage(Image image, XWPFDocument document) {
        String altText = image.getTitle() != null ? image.getTitle().toString() : "图片";
        String url = image.getUrl() != null ? image.getUrl().toString() : "";
        
        // 创建图片标题段落
        XWPFParagraph titleParagraph = document.createParagraph();
        XWPFRun titleRun = titleParagraph.createRun();
        titleRun.setText("🖼️ 图片：" + altText);
        titleRun.setFontSize(12);
        titleRun.setBold(true);
        titleRun.setColor("333333");
        setParagraphSpacing(titleParagraph, 60, 30);
        
        // 创建图片信息段落
        XWPFParagraph imageParagraph = document.createParagraph();
        XWPFRun imageRun = imageParagraph.createRun();
        imageParagraph.setAlignment(ParagraphAlignment.CENTER);
        
        // 插入图片占位符和信息
        imageRun.setText("[图片] - " + altText);
        imageRun.setFontSize(11);
        imageRun.setColor("666666");
        
        if (!url.isEmpty()) {
            imageRun.addBreak();
            imageRun.setText("图片地址：" + url);
        }
        
        // 添加图片说明
        XWPFParagraph noteParagraph = document.createParagraph();
        XWPFRun noteRun = noteParagraph.createRun();
        noteRun.setText("📌 说明：若图片无法直接显示，请复制链接到浏览器中查看。");
        noteRun.setFontSize(10);
        noteRun.setColor("666666");
        noteRun.setItalic(true);
        
        setParagraphSpacing(noteParagraph, 0, 60);
    }

    /**
     * 获取节点文本内容
     * @param node 节点
     * @return 文本内容
     */
    private String getNodeText(Node node) {
        StringBuilder text = new StringBuilder();
        Node child = node.getFirstChild();
        
        while (child != null) {
            if (child instanceof Text) {
                text.append(((Text) child).getChars());
            } else if (child instanceof Code) {
                text.append(((Code) child).getChars());
            } else {
                // 递归处理子节点
                text.append(getNodeText(child));
            }
            child = child.getNext();
        }
        
        return text.toString().trim();
    }

    /**
     * 设置段落间距
     * @param paragraph 段落
     * @param before 段前间距（单位：twips，1英寸=1440 twips）
     * @param after 段后间距
     */
    private void setParagraphSpacing(XWPFParagraph paragraph, int before, int after) {
        paragraph.setSpacingBefore(before);
        paragraph.setSpacingAfter(after);
        paragraph.setSpacingLineRule(LineSpacingRule.AUTO);
    }
}