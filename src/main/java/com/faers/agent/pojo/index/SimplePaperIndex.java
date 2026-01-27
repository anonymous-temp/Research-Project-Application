package com.faers.agent.pojo.index;

import com.faers.agent.pojo.annotation.EsDocument;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;

import java.util.List;

/**
 * 合并检索字段简化检索条件simple文献索引
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@EsDocument(index = "simple_literature_index")
public class SimplePaperIndex {
    /**
     *文献id
     */
    @Id
    private String id;
    /**
     * 文献标题
     */
    private String title;
    /**
     * 用于检索的合并的字段
     */
    private String search;
    /**
     * 年份
     */
    private String year;
    /**
     * 老版本文献所属分类
     */
    private List<Integer> type;
    /**
     * 新版本文献所属
     */
    private List<Integer> lastNewType;
    /**
     * 影响因子
     */
    private Double jcr;

    /**
     * 文献语言，中文zh，英文en
     */
    private String language;
    /**
     * 关键词
     */
    private List<String> allKeyword;
    /**
     * 研究对象
     */
    private List<String> p;
    /**
     * 实验-对照组
     */
    private List<String> ic;
    /**
     * 是否为核心期刊
     */
    private List<String> journalDivision;

    /**
     * 年份权重
     */
    private Double yearWeight;
    /**
     * 分区及影响因子权重 （目前jcr最大为254.7）
     */
    private Double coreWeight;
    /**
     * 文献类型权重
     */
    private Double typeWeight;
}
