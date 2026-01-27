package com.faers.agent.pojo;

import java.util.List;

/**
 * 项目申报书专用的 ES 文献 DTO
 * 仅保留与课题立项依据、研究现状、研究方法等相关的关键字段，
 * 用于序列化为 JSON 提供给大模型使用。
 */
public class ProjectApplicationLiteratureDTO {

    // 基本文献信息
    private String id;
    private String title;
    private String author;
    private List<String> authorList;  // 作者列表
    private String year;
    private String journal;
    private String type;
    private String language;  // 文献语言 (zh/en)
    private List<String> volume;  // 卷
    private List<String> issue;  // 期
    private String pages;  // 页码
    private List<String> doi;  // DOI

    // 主题与内容相关字段
    private List<String> keywords;
    private String summary;       // 摘要 / summary
    private String conclusion;    // 结论 / conclusion
    private String researchGap;   // 研究空白 / researchGap
    private String result;        // 结果 / result
    private String tldr;          // TL;DR 简要总结

    // 研究设计与质量相关字段
    private Long sampleSize;
    private Double jcr;
    private Long referencedCount;

    // 高亮信息与相关性
    private String highlightTitle;
    private String highlightSummary;
    private String highlightConclusion;
    private float score;

    // ===== Getters & Setters =====

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getYear() {
        return year;
    }

    public void setYear(String year) {
        this.year = year;
    }

    public String getJournal() {
        return journal;
    }

    public void setJournal(String journal) {
        this.journal = journal;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public List<String> getKeywords() {
        return keywords;
    }

    public void setKeywords(List<String> keywords) {
        this.keywords = keywords;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getConclusion() {
        return conclusion;
    }

    public void setConclusion(String conclusion) {
        this.conclusion = conclusion;
    }

    public String getResearchGap() {
        return researchGap;
    }

    public void setResearchGap(String researchGap) {
        this.researchGap = researchGap;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getTldr() {
        return tldr;
    }

    public void setTldr(String tldr) {
        this.tldr = tldr;
    }

    public Long getSampleSize() {
        return sampleSize;
    }

    public void setSampleSize(Long sampleSize) {
        this.sampleSize = sampleSize;
    }

    public Double getJcr() {
        return jcr;
    }

    public void setJcr(Double jcr) {
        this.jcr = jcr;
    }

    public Long getReferencedCount() {
        return referencedCount;
    }

    public void setReferencedCount(Long referencedCount) {
        this.referencedCount = referencedCount;
    }

    public String getHighlightTitle() {
        return highlightTitle;
    }

    public void setHighlightTitle(String highlightTitle) {
        this.highlightTitle = highlightTitle;
    }

    public String getHighlightSummary() {
        return highlightSummary;
    }

    public void setHighlightSummary(String highlightSummary) {
        this.highlightSummary = highlightSummary;
    }

    public String getHighlightConclusion() {
        return highlightConclusion;
    }

    public void setHighlightConclusion(String highlightConclusion) {
        this.highlightConclusion = highlightConclusion;
    }

    public float getScore() {
        return score;
    }

    public void setScore(float score) {
        this.score = score;
    }

    public List<String> getAuthorList() {
        return authorList;
    }

    public void setAuthorList(List<String> authorList) {
        this.authorList = authorList;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public List<String> getVolume() {
        return volume;
    }

    public void setVolume(List<String> volume) {
        this.volume = volume;
    }

    public List<String> getIssue() {
        return issue;
    }

    public void setIssue(List<String> issue) {
        this.issue = issue;
    }

    public String getPages() {
        return pages;
    }

    public void setPages(String pages) {
        this.pages = pages;
    }

    public List<String> getDoi() {
        return doi;
    }

    public void setDoi(List<String> doi) {
        this.doi = doi;
    }
}





