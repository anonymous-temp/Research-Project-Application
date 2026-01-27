package com.faers.agent.utils;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.List;
import java.util.Set;

public class FastjsonDeltaExtractor {
    public static class DeltaEntry {
        private String path;
        private Object value;

        public DeltaEntry(String path, Object value) {
            this.path = path;
            this.value = value;
        }

        @Override
        public String toString() {
            return "Path: " + path + ", Value: " + value;
        }

        public Object getValue() {
            return value;
        }
    }

    /**
     * 递归遍历 JSONObject/JSONArray，查找所有 'delta' 字段
     */
    public static void findAllDeltaFields(Object obj, String currentPath, List<DeltaEntry> results) {
        if (obj instanceof String) {
            String jsonString = (String) obj;
            try {
                obj = JSONObject.parseObject(jsonString);
            } catch (Exception ignored) {}
        }
        // 如果是 JSONObject
        if (obj instanceof JSONObject) {
            JSONObject jsonObject = (JSONObject) obj;
            Set<String> keySet = jsonObject.keySet();

            for (String key : keySet) {
                Object value = jsonObject.get(key);
                String newPath = currentPath.isEmpty() ? key : currentPath + "." + key;

                if ("delta".equals(key)) {
                    results.add(new DeltaEntry(newPath, value));
                }

                findAllDeltaFields(value, newPath, results);
            }
        }
        // 如果是 JSONArray
        else if (obj instanceof JSONArray) {
            JSONArray jsonArray = (JSONArray) obj;

            for (int i = 0; i < jsonArray.size(); i++) {
                Object value = jsonArray.get(i);
                String newPath = currentPath + "[" + i + "]";
                findAllDeltaFields(value, newPath, results);
            }
        }
        // 其他类型（基本类型、null）不处理
    }
}
