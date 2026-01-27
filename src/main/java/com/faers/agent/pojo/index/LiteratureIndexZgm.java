package com.faers.agent.pojo.index;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

import java.util.List;

@Document(indexName = "literature_index_zgm_20251001")
//@Setting(replicas = 0, refreshInterval = "30s")
public class LiteratureIndexZgm {

    @Id
    @Field(type = FieldType.Text, fielddata = true)
    private String id;

    @Field(type = FieldType.Text)
    private String _class;

    @Field(type = FieldType.Keyword)
    private String allKeyword;

    @Field(type = FieldType.Text)
    private String allKeywordType;

    @Field(type = FieldType.Double)
    private Double allWeight;

    @Field(type = FieldType.Text)
    private String author;

    @Field(type = FieldType.Keyword)
    private String authorRegion;

    @Field(type = FieldType.Keyword)
    private String authorUuid;

    @Field(type = FieldType.Keyword)
    private String belong;

    @Field(type = FieldType.Text)
    private String clearTitle;

    @Field(type = FieldType.Text, analyzer = "aliws_synonym")
    private String conclusion;

    @Field(type = FieldType.Double)
    private Double coreWeight;

    @Field(type = FieldType.Keyword)
    private String country;

    @Field(type = FieldType.Keyword)
    private String day;

    @Field(type = FieldType.Keyword)
    private String doi;

    @Field(type = FieldType.Integer)
    private Integer dupNum;

    @Field(type = FieldType.Keyword)
    private String firstAuthor;

    @Field(type = FieldType.Keyword)
    private String firstAuthorAddress;

    @Field(type = FieldType.Keyword)
    private String firstAuthorUuid;

    @Field(type = FieldType.Keyword)
    private String ic;

    @Field(type = FieldType.Keyword)
    private String indexYear;

    @Field(type = FieldType.Integer)
    private Integer isIncomplete;

    @Field(type = FieldType.Double)
    private Double jcr;

    @Field(type = FieldType.Text)
    private String journal;

    @Field(type = FieldType.Text)
    private String journalDivision;

    @Field(type = FieldType.Keyword)
    private List<String> keywords;

    @Field(type = FieldType.Keyword)
    private String language;

    @Field(type = FieldType.Keyword)
    private String lastNewType;

    @Field(type = FieldType.Keyword)
    private String month;

    @Field(type = FieldType.Keyword)
    private String o;

    @Field(type = FieldType.Keyword)
    private String p;

    @Field(type = FieldType.Keyword)
    private String pdfName;

    @Field(type = FieldType.Keyword)
    private String quality;

    @Field(type = FieldType.Keyword)
    private String referenceCount;

    @Field(type = FieldType.Long)
    private Long referencedCount;

    @Field(type = FieldType.Text, analyzer = "aliws_synonym")
    private String researchGap;

    @Field(type = FieldType.Text, analyzer = "aliws_synonym")
    private String result;

    @Field(type = FieldType.Long)
    private Long sampleSize;

    @Field(type = FieldType.Text)
    private String showAuthorAddress;

    @Field(type = FieldType.Text, analyzer = "aliws_synonym")
    private String summary;

    @Field(type = FieldType.Keyword)
    private String table;

    @Field(type = FieldType.Text, analyzer = "aliws_synonym")
    private String themWord;

    @Field(type = FieldType.Keyword)
    private String themeClassify;

    @Field(type = FieldType.Text, analyzer = "aliws_synonym")
    private String title;

    @Field(type = FieldType.Text, analyzer = "aliws_synonym")
    private String titleQuestion;

    @Field(type = FieldType.Text, analyzer = "aliws_synonym")
    private String tldr;

    @Field(type = FieldType.Keyword)
    private String type;

    @Field(type = FieldType.Double)
    private Double typeWeight;

    @Field(type = FieldType.Keyword)
    private String year;

    @Field(type = FieldType.Double)
    private Double yearWeight;

    @Field(type = FieldType.Keyword)
    private String zteNewtype;

    // 构造函数
    public LiteratureIndexZgm() {
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String get_class() {
        return _class;
    }

    public void set_class(String _class) {
        this._class = _class;
    }

    public String getAllKeyword() {
        return allKeyword;
    }

    public void setAllKeyword(String allKeyword) {
        this.allKeyword = allKeyword;
    }

    public String getAllKeywordType() {
        return allKeywordType;
    }

    public void setAllKeywordType(String allKeywordType) {
        this.allKeywordType = allKeywordType;
    }

    public Double getAllWeight() {
        return allWeight;
    }

    public void setAllWeight(Double allWeight) {
        this.allWeight = allWeight;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getAuthorRegion() {
        return authorRegion;
    }

    public void setAuthorRegion(String authorRegion) {
        this.authorRegion = authorRegion;
    }

    public String getAuthorUuid() {
        return authorUuid;
    }

    public void setAuthorUuid(String authorUuid) {
        this.authorUuid = authorUuid;
    }

    public String getBelong() {
        return belong;
    }

    public void setBelong(String belong) {
        this.belong = belong;
    }

    public String getClearTitle() {
        return clearTitle;
    }

    public void setClearTitle(String clearTitle) {
        this.clearTitle = clearTitle;
    }

    public String getConclusion() {
        return conclusion;
    }

    public void setConclusion(String conclusion) {
        this.conclusion = conclusion;
    }

    public Double getCoreWeight() {
        return coreWeight;
    }

    public void setCoreWeight(Double coreWeight) {
        this.coreWeight = coreWeight;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getDay() {
        return day;
    }

    public void setDay(String day) {
        this.day = day;
    }

    public String getDoi() {
        return doi;
    }

    public void setDoi(String doi) {
        this.doi = doi;
    }

    public Integer getDupNum() {
        return dupNum;
    }

    public void setDupNum(Integer dupNum) {
        this.dupNum = dupNum;
    }

    public String getFirstAuthor() {
        return firstAuthor;
    }

    public void setFirstAuthor(String firstAuthor) {
        this.firstAuthor = firstAuthor;
    }

    public String getFirstAuthorAddress() {
        return firstAuthorAddress;
    }

    public void setFirstAuthorAddress(String firstAuthorAddress) {
        this.firstAuthorAddress = firstAuthorAddress;
    }

    public String getFirstAuthorUuid() {
        return firstAuthorUuid;
    }

    public void setFirstAuthorUuid(String firstAuthorUuid) {
        this.firstAuthorUuid = firstAuthorUuid;
    }

    public String getIc() {
        return ic;
    }

    public void setIc(String ic) {
        this.ic = ic;
    }

    public String getIndexYear() {
        return indexYear;
    }

    public void setIndexYear(String indexYear) {
        this.indexYear = indexYear;
    }

    public Integer getIsIncomplete() {
        return isIncomplete;
    }

    public void setIsIncomplete(Integer isIncomplete) {
        this.isIncomplete = isIncomplete;
    }

    public Double getJcr() {
        return jcr;
    }

    public void setJcr(Double jcr) {
        this.jcr = jcr;
    }

    public String getJournal() {
        return journal;
    }

    public void setJournal(String journal) {
        this.journal = journal;
    }

    public String getJournalDivision() {
        return journalDivision;
    }

    public void setJournalDivision(String journalDivision) {
        this.journalDivision = journalDivision;
    }

    public List<String> getKeywords() {
        return keywords;
    }

    public void setKeywords(List<String> keywords) {
        this.keywords = keywords;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getLastNewType() {
        return lastNewType;
    }

    public void setLastNewType(String lastNewType) {
        this.lastNewType = lastNewType;
    }

    public String getMonth() {
        return month;
    }

    public void setMonth(String month) {
        this.month = month;
    }

    public String getO() {
        return o;
    }

    public void setO(String o) {
        this.o = o;
    }

    public String getP() {
        return p;
    }

    public void setP(String p) {
        this.p = p;
    }

    public String getPdfName() {
        return pdfName;
    }

    public void setPdfName(String pdfName) {
        this.pdfName = pdfName;
    }

    public String getQuality() {
        return quality;
    }

    public void setQuality(String quality) {
        this.quality = quality;
    }

    public String getReferenceCount() {
        return referenceCount;
    }

    public void setReferenceCount(String referenceCount) {
        this.referenceCount = referenceCount;
    }

    public Long getReferencedCount() {
        return referencedCount;
    }

    public void setReferencedCount(Long referencedCount) {
        this.referencedCount = referencedCount;
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

    public Long getSampleSize() {
        return sampleSize;
    }

    public void setSampleSize(Long sampleSize) {
        this.sampleSize = sampleSize;
    }

    public String getShowAuthorAddress() {
        return showAuthorAddress;
    }

    public void setShowAuthorAddress(String showAuthorAddress) {
        this.showAuthorAddress = showAuthorAddress;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getTable() {
        return table;
    }

    public void setTable(String table) {
        this.table = table;
    }

    public String getThemWord() {
        return themWord;
    }

    public void setThemWord(String themWord) {
        this.themWord = themWord;
    }

    public String getThemeClassify() {
        return themeClassify;
    }

    public void setThemeClassify(String themeClassify) {
        this.themeClassify = themeClassify;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getTitleQuestion() {
        return titleQuestion;
    }

    public void setTitleQuestion(String titleQuestion) {
        this.titleQuestion = titleQuestion;
    }

    public String getTldr() {
        return tldr;
    }

    public void setTldr(String tldr) {
        this.tldr = tldr;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Double getTypeWeight() {
        return typeWeight;
    }

    public void setTypeWeight(Double typeWeight) {
        this.typeWeight = typeWeight;
    }

    public String getYear() {
        return year;
    }

    public void setYear(String year) {
        this.year = year;
    }

    public Double getYearWeight() {
        return yearWeight;
    }

    public void setYearWeight(Double yearWeight) {
        this.yearWeight = yearWeight;
    }

    public String getZteNewtype() {
        return zteNewtype;
    }

    public void setZteNewtype(String zteNewtype) {
        this.zteNewtype = zteNewtype;
    }

    @Override
    public String toString() {
        return "LiteratureIndexZgm{" +
                "id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", author='" + author + '\'' +
                ", year='" + year + '\'' +
                ", journal='" + journal + '\'' +
                ", type='" + type + '\'' +
                ", keywords=" + keywords +
                '}';
    }
}