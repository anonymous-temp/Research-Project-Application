package com.faers.agent.pojo;

import lombok.Data;

import java.util.Map;

/**
 * 项目申报书参数实体
 * 将 AnalysisContext 中的 queryParams 映射为结构化对象，便于前端展示和导出使用。
 */
@Data
public class ProjectApplicationParams {

    // 项目信息
    private String topicName;              // 课题名称 - 必填项
    private String disciplineField;        // 申报学科领域 - 必填项
    private TeamInfo teamInfo;             // 研究团队情况 - 必填项
    private String researchFocus;          // 研究重点方向 - 必填项
    private String outputLanguage;         // 报告输出语言（中文/英文）- 必填项，默认为中文

    /**
     * 研究团队情况内部类
     */
    @Data
    public static class TeamInfo {
        private String priorResearch;      // 前期研究成果（论文、专利、前期研究数据等）
        private String clinicalData;       // 临床数据资源或合作医院
        private String teamBackground;     // 团队成员专业背景（AI、药学、临床医学等）
    }

    /**
     * 从 queryParams Map 构建 ProjectApplicationParams
     */
    @SuppressWarnings("unchecked")
    public static ProjectApplicationParams fromMap(Map<String, Object> params) {
        ProjectApplicationParams p = new ProjectApplicationParams();
        if (params == null) {
            return p;
        }

        p.setTopicName(getString(params.get("topicName")));
        p.setDisciplineField(getString(params.get("disciplineField")));
        p.setResearchFocus(getString(params.get("researchFocus")));
        
        // 处理输出语言参数，默认为中文
//        String outputLanguage = getString(params.get("outputLanguage"));
        p.setOutputLanguage(getString(params.get("outputLanguage")));
//        p.setOutputLanguage(outputLanguage != null ? outputLanguage : "中文");

        // 处理研究团队信息
        if (params.containsKey("teamInfo")) {
            Object teamInfoObj = params.get("teamInfo");
            if (teamInfoObj instanceof Map) {
                Map<String, Object> teamMap = (Map<String, Object>) teamInfoObj;
                TeamInfo teamInfo = new TeamInfo();
                teamInfo.setPriorResearch(getString(teamMap.get("priorResearch")));
                teamInfo.setClinicalData(getString(teamMap.get("clinicalData")));
                teamInfo.setTeamBackground(getString(teamMap.get("teamBackground")));
                p.setTeamInfo(teamInfo);
            } else if (teamInfoObj instanceof TeamInfo) {
                p.setTeamInfo((TeamInfo) teamInfoObj);
            }
        }

        return p;
    }

    private static String getString(Object v) {
        return v == null ? null : v.toString().trim();
    }

    private static Boolean getBoolean(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Boolean) {
            return (Boolean) v;
        }
        if (v instanceof String) {
            String s = ((String) v).trim().toLowerCase();
            return s.equals("true") || s.equals("是") || s.equals("有") || s.equals("yes");
        }
        return Boolean.valueOf(v.toString());
    }

    private static Double getDouble(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Double) {
            return (Double) v;
        }
        if (v instanceof Number) {
            return ((Number) v).doubleValue();
        }
        try {
            return Double.valueOf(v.toString().replaceAll("[^\\d.]", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}




