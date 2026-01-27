//package com.faers.agent.tools;
//
//import dev.langchain4j.web.search.WebSearchTool;
//import org.springframework.ai.support.ToolCallbacks;
//import org.springframework.ai.tool.ToolCallback;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
///**
// * 集中的工具注册类
// */
//@Configuration
//public class ToolRegistration {
//
//    @Value("${search-api.api-key}")
//    private String searchApiKey;
//
//    @Value("${search-api.bo-cha.key}")
//    private String bochaSearchApiKey;
//
//    @Bean
//    public ToolCallback[] allTools() {
//        WebSearchTool webSearchTool = new WebSearchTool(searchApiKey);
//        BochaWebSearchTool bochaWebSearchTool = new BochaWebSearchTool(bochaSearchApiKey);
//        PDFGenerationTool pdfGenerationTool = new PDFGenerationTool();
//        MarkdownGenerationTool markdownGenerationTool = new MarkdownGenerationTool();
//        ZipArchiveTool zipArchiveTool = new ZipArchiveTool();
//        OutlineFillingTool outlineFillingTool = new OutlineFillingTool(zipArchiveTool);
//        KnowledgeSearchTool knowledgeSearchTool = new KnowledgeSearchTool();
//        TerminateTool terminateTool = new TerminateTool();
//        return ToolCallbacks.from(
//                fileOperationTool,
////                webSearchTool,
//                bochaWebSearchTool,
//                webScrapingTool,
//                resourceDownloadTool,
//                terminalOperationTool,
//                pdfGenerationTool,
//                markdownGenerationTool,
//                outlineFillingTool,
//                knowledgeSearchTool,
//                zipArchiveTool,
//                terminateTool
//        );
//    }
//}
