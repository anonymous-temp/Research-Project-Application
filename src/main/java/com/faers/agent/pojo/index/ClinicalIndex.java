package com.faers.agent.pojo.index;

import com.faers.agent.pojo.annotation.EsDocument;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;

import java.util.List;

/**
 * 临床试验 elasticSearch的索引
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@EsDocument(index = "clinical_index_gxp")
public class ClinicalIndex {

    /**
     * 临床试验id
     */
    @Id
    private String id;

    /**
     * 临床试验属于分类
     */
    private String belong;

    /**
     * 试验题目
     */
    private String publicTitle;

    /**
     * 适应症
     */
    private List<String> condition;

    /**
     * 干预措施
     */
    private List<String> intervention;

    /**
     * 注册时间
     */
    private String registerDate;

    /**
     * 样本量
     */
    private String sampleSize;

    /**
     * 招募状态
     */
    private String recruitmentStatus;

    /**
     * 试验阶段
     */
    private String studyPhase;

    /**
     * 关联文章
     */
    private Integer reference;

    /**
     * 研究类型
     */
    private String studyType;
    /**
     * 登记号
     */
    private String registerNo;
}
