package com.faers.agent.pojo.index;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

import java.util.List;

@Data
@Document(indexName = "literature_index_wsz")
//@Setting(settingPath = "es-config/analysis-settings.json") // 如果有自定义分析器配置
public class LiteratureDocument {
    
    @Id
    @MultiField(
        mainField = @Field(type = FieldType.Text),
        otherFields = {
            @InnerField(suffix = "keyword", type = FieldType.Keyword)
        }
    )
    private String id;
    
    @Field(type = FieldType.Text)
    private String _class;
    
    @Field(type = FieldType.Keyword)
    private String allKeyword;
    
    @MultiField(
        mainField = @Field(type = FieldType.Text),
        otherFields = {
            @InnerField(suffix = "keyword", type = FieldType.Keyword)
        }
    )
    private String allKeywordType;
    
    @Field(type = FieldType.Double)
    private Double allWeight;
    
    @MultiField(
        mainField = @Field(type = FieldType.Text),
        otherFields = {
            @InnerField(suffix = "keyword", type = FieldType.Keyword)
        }
    )
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
    
    @MultiField(
        mainField = @Field(type = FieldType.Text),
        otherFields = {
            @InnerField(suffix = "keyword", type = FieldType.Keyword)
        }
    )
    private String journal;
    
    @MultiField(
        mainField = @Field(type = FieldType.Text),
        otherFields = {
            @InnerField(suffix = "keyword", type = FieldType.Keyword)
        }
    )
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
    
    @MultiField(
        mainField = @Field(type = FieldType.Text),
        otherFields = {
            @InnerField(suffix = "keyword", type = FieldType.Keyword)
        }
    )
    private String showAuthorAddress;
    
    @Field(type = FieldType.Text, analyzer = "aliws_synonym")
    private String summary;
    
    @Field(type = FieldType.Keyword)
    private String table;
    
    @MultiField(
        mainField = @Field(type = FieldType.Text, analyzer = "aliws_synonym"),
        otherFields = {
            @InnerField(suffix = "keyword", type = FieldType.Keyword)
        }
    )
    private String themWord;
    
    @Field(type = FieldType.Keyword)
    private String themeClassify;
    
    @Field(type = FieldType.Text, analyzer = "aliws_synonym")
    private String title;
    
    @MultiField(
        mainField = @Field(type = FieldType.Text, analyzer = "aliws_synonym"),
        otherFields = {
            @InnerField(suffix = "keyword", type = FieldType.Keyword)
        }
    )
    private String titleQuestion;
    
    @MultiField(
        mainField = @Field(type = FieldType.Text, analyzer = "aliws_synonym"),
        otherFields = {
            @InnerField(suffix = "keyword", type = FieldType.Keyword)
        }
    )
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
}