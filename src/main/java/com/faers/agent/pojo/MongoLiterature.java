package com.faers.agent.pojo;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;

import java.util.List;
import java.util.Map;

/**
 * 新检索架构中mongo中文献的对应实体类
 * @author zgm
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MongoLiterature {
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
     * 文献描述
     */
    private String summary;

    /**
     * 文献期刊
     */
    private String journal;
    /**
     * 年份
     */
    private String year;
    /**
     * 文献所属
     */
    private List<Integer> type;
    /**
     * 结局指标
     */
    private List<String> outType;
    /**
     * 文献作者
     */
    private List<String> author;
    //*******************************************
    /**
     * 作者单位
     */
    private List<String> authorFacility;
    //********************************************
    /**
     * 影响因子
     */
    private Double jcr;

    /**
     * 文献来源的url
     */
    private List<String> url;

    /**
     * 文献语言，中文zh，英文en
     */
    private String language;

    /**
     * 结论句
     */
    private String conclusion;

    private String dbPdfName;

    /**
     * 样本量
     */
    private Long sampleSize;

    /**
     * 质量等级
     */
    private String quality;

    /**
     * 研究对象
     */
    private List<String> p;

    /**
     * 实验-对照组
     */
    private List<String> ic;

    /**
     * 结局指标
     */
    private List<String> o;

    private String objective;


    private String result;

    /**
     * 期刊简写
     */
    private String abbreviatedJournal;

    /**
     * 期刊全写
     */
    private String fullJournal;

    private String mainUrl;

    private List<String> allKeyword;

    /**
     * 关键词的类型
     */
    private List<String> allKeywordType;

    //private List<String> showAuthorAddress;

    private String pdfName;
    //***********************************************
    /**
     * 卷
     */
    private List<String> volume;
    /**
     * 期
     */
    private List<String> issue;
    /**
     * 页码
     */
    private String pages;
    /**
     * doi
     */
    private List<String> doi;
    /**
     * 文献类型
     */
    private List<String> publicationType;
    /**
     * 期刊ISSN号
     */
    private List<String> issn;
    /**
     * 文献来源
     */
    private List<String> belong;
    /**
     * 出版国家
     */
    private List<String> countryOfPublication;

    //******************20230102新加字段**************************

    /**
     * 记录文献属于哪个核心的字段------中文文献特有字段
     */
    private List<String> recognizedKernelJournals;

    /**
     * 更多链接
     */
    private Map<String, String> urlDict;

    /**
     * 重复文献的来源及数量
     */
    private String dupBelongNums;

    /**
     * 引用文献数量
     */
    private Integer referencedCount;

    /**
     * 文献摘要的简写---英文文献特有
     */
    private String tldr;

    /**
     * 英文文献分区---英文文献特有
     */
    private List<String> journalDivision;

    /**
     * 引用文献id的集合
     */
    private List<String> referencesList;

    /**
     * 被引用文献id的集合
     */
    private List<String> quotedList;

    /**
     * method
     */
    private String method;
    /**
     * 作者id
     */
    private List<String> authorUuid;

    /**
     * 1-残缺，0-非残缺
     */
    private Integer isIncomplete;

    private String syPdfUrl;

    /**
     * pubmed图片
     */
    private List<String> pubmedPicture;

    /**
     * 知网和pubmed标准引用格式
     */
    private Map<String, String> cites;
}
