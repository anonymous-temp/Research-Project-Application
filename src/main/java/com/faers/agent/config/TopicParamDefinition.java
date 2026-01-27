package com.faers.agent.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TopicParamDefinition {

    // 参数名称
    private String paramName;
    // 参数类型
    private ParamType paramType;
    // 中文描述
    private String description;
    // 是否必填
    private boolean required;
    // 直接询问策略
    private String directAskText;
    // 选项建议策略
    private List<String> options;
    private String optionsAskText;
    // 澄清确认策略
    private String clarificationAskText;
    // 默认值
    private Object defaultValue;
    // 正则表达式验证
    private String regex;

    // 参数类型枚举
    public enum ParamType {
        STRING,      // 字符串类型
        BOOLEAN,     // 布尔类型
        OBJECT,      // 对象类型
        NUMBER       // 数字类型
    }

    // 注册所有参数定义
    public static List<TopicParamDefinition> ALL = Arrays.asList(
            // 课题名称
            TopicParamDefinition.builder()
                    .paramName("topicName")
                    .paramType(ParamType.STRING)
                    .description("课题名称")
                    .required(true)
                    .directAskText("请输入您的课题名称")
                    .build(),
            // 申报学科领域
            TopicParamDefinition.builder()
                    .paramName("disciplineField")
                    .paramType(ParamType.STRING)
                    .description("申报学科领域")
                    .required(true)
                    .directAskText("请输入申报的学科代码（例如：H2701医学信息学、C0708生物信息学、H3515临床药理学等）")
                    .build(),
            // 研究团队情况
            TopicParamDefinition.builder()
                    .paramName("teamInfo")
                    .paramType(ParamType.OBJECT)
                    .description("研究团队情况")
                    .required(true)
                    .directAskText("请提供研究团队情况，包括：\n(1) 您或团队在相关领域已有哪些研究成果？（如已发表论文、专利、前期研究数据等）\n(2) 是否有相关的临床数据资源或合作医院？\n(3) 团队成员的专业背景（AI、药学、临床医学等）")
                    .build(),
            // 研究重点方向
            TopicParamDefinition.builder()
                    .paramName("researchFocus")
                    .paramType(ParamType.STRING)
                    .description("研究重点方向")
                    .required(true)
                    .directAskText("请描述您希望侧重的研究方向")
                    .build(),
            // 输出语言格式
            TopicParamDefinition.builder()
                    .paramName("outputLanguage")
                    .paramType(ParamType.STRING)
                    .description("输出格式")
                    .required(true)
                    .directAskText("请选择报告输出语言（中文/英文），默认为中文")
                    .options(Arrays.asList("中文", "英文"))
                    .optionsAskText("请选择报告输出语言")
                    .build()
    );

    // 根据参数名称获取参数定义
    public static TopicParamDefinition getByParamName(String paramName) {
        for (TopicParamDefinition paramDef : ALL) {
            if (paramDef.getParamName().equals(paramName)) {
                return paramDef;
            }
        }
        return null;
    }

    // 获取所有必填参数
    public static List<TopicParamDefinition> getRequiredParams() {
        List<TopicParamDefinition> requiredParams = new java.util.ArrayList<>();
        for (TopicParamDefinition paramDef : ALL) {
            if (paramDef.isRequired()) {
                requiredParams.add(paramDef);
            }
        }
        return requiredParams;
    }

    // 获取所有可选参数
    public static List<TopicParamDefinition> getOptionalParams() {
        List<TopicParamDefinition> optionalParams = new java.util.ArrayList<>();
        for (TopicParamDefinition paramDef : ALL) {
            if (!paramDef.isRequired()) {
                optionalParams.add(paramDef);
            }
        }
        return optionalParams;
    }

    // 获取参数默认值映射
    public static Map<String, Object> getDefaultParamMap() {
        Map<String, Object> defaultMap = new HashMap<>();
        for (TopicParamDefinition paramDef : ALL) {
            if (paramDef.getDefaultValue() != null) {
                defaultMap.put(paramDef.getParamName(), paramDef.getDefaultValue());
            }
        }
        return defaultMap;
    }
}