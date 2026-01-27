// com/faers/agent/utils/ParamUtils.java
package com.faers.agent.utils;

import com.faers.agent.config.TopicParamDefinition;

import java.time.YearMonth;
import java.util.*;

public class ParamUtils {

    public static void updateParams(Map<String, Object> current, Map<String, Object> updates) {
        for (TopicParamDefinition def : TopicParamDefinition.ALL) {
            if (updates.containsKey(def.getParamName())) {
                Object value = updates.get(def.getParamName());
                if (value != null) {
                    // 对时间参数进行自动标准化
                    String key = def.getParamName();
                    if ("startDate".equals(key)) {
                        value = normalizeStartDate(value.toString());
                    } else if ("endDate".equals(key)) {
                        value = normalizeEndDate(value.toString());
                    }
                    current.put(def.getParamName(), value);
                }
            }
        }
    }

    public static String buildSummary(Map<String, Object> params) {
        StringBuilder sb = new StringBuilder();
        for (TopicParamDefinition def : TopicParamDefinition.ALL) {
            Object value = params.get(def.getParamName());
            if (value == null) continue;

            String label = getLabel(def.getParamName());
            
            // 根据参数类型格式化显示
            if (value instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> list = (List<String>) value;
                if (!list.isEmpty()) {
                    sb.append("- ").append(label).append("：").append(String.join(", ", list)).append("\n");
                }
            } else if (value instanceof Boolean) {
                sb.append("- ").append(label).append("：").append((Boolean) value ? "是" : "否").append("\n");
            } else if (value instanceof Number) {
                sb.append("- ").append(label).append("：").append(value).append("\n");
            } else if (value instanceof String && !((String) value).isEmpty()) {
                sb.append("- ").append(label).append("：").append(value).append("\n");
            } else {
                // 处理对象类型
                sb.append("- ").append(label).append("：").append(value.toString()).append("\n");
            }
        }
        return sb.toString();
    }

    public static List<String> getMissingAskableSuggestions(Map<String, Object> params) {
        List<String> suggestions = new ArrayList<>();
        for (TopicParamDefinition def : TopicParamDefinition.ALL) {
            // TopicParamDefinition没有isAskIfMissing方法，我们可以根据required和directAskText来判断
            if (def.isRequired() && !params.containsKey(def.getParamName())) {
                if (def.getOptions() != null && !def.getOptions().isEmpty()) {
                    suggestions.add(def.getOptionsAskText());
                } else {
                    suggestions.add(def.getDirectAskText());
                }
            }
        }
        return suggestions;
    }

    public static boolean isComplete(Map<String, Object> params) {
        if (params == null) {
            return false;
        }
        
        for (TopicParamDefinition def : TopicParamDefinition.ALL) {
            if (def.isRequired() && !isParamValid(params, def.getParamName())) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 检查单个参数是否有效（存在且非空）
     */
    public static boolean isParamValid(Map<String, Object> params, String paramName) {
        if (params == null || !params.containsKey(paramName)) {
            return false;
        }
        
        Object value = params.get(paramName);
        if (value == null) {
            return false;
        }
        
        // 处理字符串类型
        if (value instanceof String) {
            if (((String) value).trim().isEmpty()) {
                return false;
            }
        }
        // 处理数字类型
        else if (value instanceof Number) {
            // 数字类型，0或0.0视为无效
            if (value instanceof Double && ((Double) value) == 0.0) {
                return false;
            } else if (value instanceof Integer && ((Integer) value) == 0) {
                return false;
            } else if (value instanceof Float && ((Float) value) == 0.0f) {
                return false;
            }
        }
        // 处理布尔类型
        else if (value instanceof Boolean) {
            // 布尔类型本身是有效的，无论值是什么
        }
        // 处理研究团队信息对象类型（复合参数）
        else if (value instanceof Map && "teamInfo".equals(paramName)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> teamMap = (Map<String, Object>) value;
            // 检查团队信息的子参数是否完整
            if (teamMap.isEmpty()) {
                return false;
            }
            // 检查每个子参数是否非空
            for (Map.Entry<String, Object> entry : teamMap.entrySet()) {
                Object subValue = entry.getValue();
                if (subValue == null) {
                    return false;
                }
                if (subValue instanceof String && ((String) subValue).trim().isEmpty()) {
                    return false;
                }
            }
        }
        // 处理其他对象类型
        else if (value instanceof Map) {
            // 其他对象类型，只要不为空就视为有效
            @SuppressWarnings("unchecked")
            Map<?, ?> mapValue = (Map<?, ?>) value;
            if (mapValue.isEmpty()) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 打印参数收集状态
     */
    public static void printParamCollectionStatus(Map<String, Object> params) {
        System.out.println("\n=== 参数收集状态 ===");
        
        // 输出已收集的参数
        System.out.println("✅ 已收集的参数：");
        boolean hasCollectedParams = false;
        for (TopicParamDefinition def : TopicParamDefinition.ALL) {
            if (isParamValid(params, def.getParamName())) {
                hasCollectedParams = true;
                String label = getLabel(def.getParamName());
                Object value = params.get(def.getParamName());
                System.out.printf("   %s: %s%n", label, value);
            }
        }
        
        if (!hasCollectedParams) {
            System.out.println("   暂无已收集的参数");
        }
        
        // 输出缺失的参数
        System.out.println("❌ 缺失的参数：");
        List<String> missingParams = new ArrayList<>();
        for (TopicParamDefinition def : TopicParamDefinition.ALL) {
            if (!isParamValid(params, def.getParamName())) {
                missingParams.add(getLabel(def.getParamName()));
            }
        }
        
        if (missingParams.isEmpty()) {
            System.out.println("   所有参数已收集完整");
        } else {
            System.out.printf("   %s%n", String.join(", ", missingParams));
        }
        System.out.println("===================\n");
    }

    // === 时间标准化 ===
    public static String normalizeStartDate(String input) {
        if (input == null || input.isEmpty()) return null;
        input = input.trim();
        try {
            if (input.matches("\\d{4}")) return input + "-01-01";
            if (input.matches("\\d{4}-\\d{2}")) return input + "-01";
            if (input.matches("\\d{4}-\\d{2}-\\d{2}")) return input;
        } catch (Exception ignored) {}
        return null;
    }

    public static String normalizeEndDate(String input) {
        if (input == null || input.isEmpty()) return null;
        input = input.trim();
        try {
            if (input.matches("\\d{4}")) return input + "-12-31";
            if (input.matches("\\d{4}-\\d{2}")) {
                String[] parts = input.split("-");
                int year = Integer.parseInt(parts[0]);
                int month = Integer.parseInt(parts[1]);
                int days = YearMonth.of(year, month).lengthOfMonth();
                return String.format("%d-%02d-%02d", year, month, days);
            }
            if (input.matches("\\d{4}-\\d{2}-\\d{2}")) return input;
        } catch (Exception ignored) {}
        return null;
    }

    private static String getLabel(String key) {
        switch (key) {
            case "topicName": return "课题名称";
            case "disciplineField": return "申报学科领域";
            case "teamInfo": return "研究团队情况";
            case "researchFocus": return "研究重点方向";
            case "outputLanguage": return "输出格式";
            case "startDate": return "开始时间";
            case "endDate": return "结束时间";
            default: return key;
        }
    }
}
