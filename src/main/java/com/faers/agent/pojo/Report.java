package com.faers.agent.pojo;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * 报告实体类
 * 用于管理生成的各种报告
 */
@Document(collection = "reports")
@Data
public class Report {
    @Id
    private String reportId;
    
    // 关联的会话ID
    private String sessionId;
    
    // 报告所属用户ID
    private String userId;
    
    // 报告名称
    private String reportName;
    
    // 报告类型（如：综合报告、安全性分析报告、文献检索报告等）
    private String reportType;
    
    // 报告内容
    private String content;
    
    // 报告状态（如：生成中、已完成、已归档等）
    private String status;
    
    // 报告文件路径（如果报告保存为文件）
    private String filePath;
    
    // 报告生成时间
    private LocalDateTime createdAt;
    
    // 报告更新时间
    private LocalDateTime updatedAt;
    
    // 报告元数据（如：参数信息、统计数据等）
    private String metadata;
    
    // 是否为最新版本
    private boolean isLatestVersion;
    
    // 版本号
    private Integer version;
    
    // 前一版本的报告ID
    private String previousVersionId;
    
    // 报告大小（字节）
    private Long size;
}
