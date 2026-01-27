package com.faers.agent.pojo;

import lombok.Data;

/**
 * 报告章节内容和摘要的分离结果
 */
@Data
public class ReportSectionResult {
    private String reportContent;
    private String summary;
}