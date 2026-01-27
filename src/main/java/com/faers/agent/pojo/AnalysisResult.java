package com.faers.agent.pojo;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 分析结果实体类
 * 用于管理各种分析的结果数据
 */
@Document(collection = "analysis_results")
@Data
public class AnalysisResult {
    @Id
    private String resultId;
    
    // 关联的会话ID
    private String sessionId;
    
    // 关联的报告ID
    private String reportId;
    
    // 分析类型（如：信号检测、文献分析、趋势分析等）
    private String analysisType;
    
    // 分析结果数据（JSON格式）
    private String resultData;
    
    // 分析状态（如：分析中、已完成、失败等）
    private String status;
    
    // 分析开始时间
    private LocalDateTime startTime;
    
    // 分析结束时间
    private LocalDateTime endTime;
    
    // 分析耗时（毫秒）
    private Long duration;
    
    // 分析参数（JSON格式）
    private String analysisParams;
    
    // 分析用户ID
    private String userId;
    
    // 错误信息（如果分析失败）
    private String errorMessage;
    
    // 分析结果摘要
    private String summary;
    
    // 分析结果的元数据
    private Map<String, Object> metadata;
    
    // 分析结果的置信度
    private Double confidence;
    
    // 是否已被标记为重要
    private boolean isImportant;
}
