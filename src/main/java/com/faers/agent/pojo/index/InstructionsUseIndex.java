package com.faers.agent.pojo.index;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

import java.util.Date;

@Data
@Document(indexName = "instructions_use_index")
//@Setting(settingPath = "es-settings.json") // 如果有自定义设置，可以在这里指定
public class InstructionsUseIndex {
    
    @Id
    @Field(type = FieldType.Text)
    private String id;
    
    @MultiField(
        mainField = @Field(type = FieldType.Text),
        otherFields = {
            @InnerField(suffix = "keyword", type = FieldType.Keyword, ignoreAbove = 256)
        }
    )
    private String _class;
    
    @MultiField(
        mainField = @Field(type = FieldType.Text, analyzer = "aliws_synonym"),
        otherFields = {
            @InnerField(suffix = "keyword", type = FieldType.Keyword, ignoreAbove = 256),
            @InnerField(suffix = "text", type = FieldType.Text)
        }
    )
    private String approveCode;
    
    @Field(type = FieldType.Keyword)
    private String approveDate;
    
    @Field(type = FieldType.Text)
    private String detailId;
    
    @MultiField(
        mainField = @Field(type = FieldType.Text, analyzer = "aliws_synonym"),
        otherFields = {
            @InnerField(suffix = "keyword", type = FieldType.Keyword, ignoreAbove = 256),
            @InnerField(suffix = "text", type = FieldType.Text)
        }
    )
    private String dosageForm;
    
    @MultiField(
        mainField = @Field(type = FieldType.Text, analyzer = "aliws_synonym"),
        otherFields = {
            @InnerField(suffix = "keyword", type = FieldType.Keyword, ignoreAbove = 256),
            @InnerField(suffix = "text", type = FieldType.Text)
        }
    )
    private String duplication;
    
    @MultiField(
        mainField = @Field(type = FieldType.Text, analyzer = "aliws_synonym"),
        otherFields = {
            @InnerField(suffix = "keyword", type = FieldType.Keyword, ignoreAbove = 256),
            @InnerField(suffix = "text", type = FieldType.Text)
        }
    )
    private String englishName;
    
    @MultiField(
        mainField = @Field(type = FieldType.Text, analyzer = "aliws_synonym"),
        otherFields = {
            @InnerField(suffix = "keyword", type = FieldType.Keyword, ignoreAbove = 256),
            @InnerField(suffix = "text", type = FieldType.Text)
        }
    )
    private String enterpriseName;
    
    @MultiField(
        mainField = @Field(type = FieldType.Text, analyzer = "aliws_synonym"),
        otherFields = {
            @InnerField(suffix = "keyword", type = FieldType.Keyword, ignoreAbove = 256),
            @InnerField(suffix = "text", type = FieldType.Text)
        }
    )
    private String genericNames;
    
    @MultiField(
        mainField = @Field(type = FieldType.Text, analyzer = "aliws_synonym"),
        otherFields = {
            @InnerField(suffix = "keyword", type = FieldType.Keyword, ignoreAbove = 256),
            @InnerField(suffix = "text", type = FieldType.Text)
        }
    )
    private String indication;
    
    @Field(type = FieldType.Text)
    private String instructionId;
    
    @Field(type = FieldType.Boolean)
    private Boolean medicineUsePdf;
    
    @Field(type = FieldType.Long)
    private Long pdfIsBad;
    
    @Field(type = FieldType.Keyword)
    private String pdfName;
    
    @Field(type = FieldType.Keyword)
    private String revisionDate;
    
    @Field(type = FieldType.Long)
    private Long selected;
    
    @MultiField(
        mainField = @Field(type = FieldType.Text, analyzer = "aliws_synonym"),
        otherFields = {
            @InnerField(suffix = "keyword", type = FieldType.Keyword, ignoreAbove = 256),
            @InnerField(suffix = "text", type = FieldType.Text)
        }
    )
    private String simpleEnglishName;
    
    @MultiField(
        mainField = @Field(type = FieldType.Text, analyzer = "aliws_synonym"),
        otherFields = {
            @InnerField(suffix = "keyword", type = FieldType.Keyword, ignoreAbove = 256),
            @InnerField(suffix = "text", type = FieldType.Text)
        }
    )
    private String simpleGenericNames;
    
    @MultiField(
        mainField = @Field(type = FieldType.Text, analyzer = "aliws_synonym"),
        otherFields = {
            @InnerField(suffix = "keyword", type = FieldType.Keyword, ignoreAbove = 256),
            @InnerField(suffix = "text", type = FieldType.Text)
        }
    )
    private String simpleIndication;
    
    @MultiField(
        mainField = @Field(type = FieldType.Text, analyzer = "aliws_synonym"),
        otherFields = {
            @InnerField(suffix = "keyword", type = FieldType.Keyword, ignoreAbove = 256),
            @InnerField(suffix = "text", type = FieldType.Text)
        }
    )
    private String simpleTradeNames;
    
    @Field(type = FieldType.Keyword)
    private String source;
    
    @MultiField(
        mainField = @Field(type = FieldType.Text, analyzer = "aliws_synonym"),
        otherFields = {
            @InnerField(suffix = "keyword", type = FieldType.Keyword, ignoreAbove = 256),
            @InnerField(suffix = "text", type = FieldType.Text)
        }
    )
    private String specifications;
    
    @MultiField(
        mainField = @Field(type = FieldType.Text, analyzer = "aliws_synonym"),
        otherFields = {
            @InnerField(suffix = "keyword", type = FieldType.Keyword, ignoreAbove = 256),
            @InnerField(suffix = "text", type = FieldType.Text)
        }
    )
    private String taboo;
    
    @MultiField(
        mainField = @Field(type = FieldType.Text, analyzer = "aliws_synonym"),
        otherFields = {
            @InnerField(suffix = "keyword", type = FieldType.Keyword, ignoreAbove = 256),
            @InnerField(suffix = "text", type = FieldType.Text)
        }
    )
    private String tradeNames;
    
    @MultiField(
        mainField = @Field(type = FieldType.Text, analyzer = "aliws_synonym"),
        otherFields = {
            @InnerField(suffix = "keyword", type = FieldType.Keyword, ignoreAbove = 256),
            @InnerField(suffix = "text", type = FieldType.Text)
        }
    )
    private String usage;
}