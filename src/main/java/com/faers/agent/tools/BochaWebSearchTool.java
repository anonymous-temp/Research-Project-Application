package com.faers.agent.tools;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.faers.agent.pojo.StreamDataCallback;
import com.google.gson.Gson;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 网页搜索工具
 */
public class BochaWebSearchTool {

    // SearchAPI 的搜索接口地址
    private static final String SEARCH_API_URL = "https://api.bocha.cn/v1/web-search";

    private final String apiKey;

    public BochaWebSearchTool(String apiKey) {
        this.apiKey = apiKey;
    }

    @Tool(description = "Search for information from Baidu Search Engine")
    public List<JSONObject> searchWebByBocha(
            @ToolParam(description = "Search query keyword") String query,
            StreamDataCallback callback
    ) {
        // 创建 OkHttpClient 实例（同步模式）

        Map<String, Object> param = new HashMap<>();
        param.put("query", query);
        param.put("summary", "true");
        param.put("include", "ncbi.nlm.nih.gov|clinicaltrials.gov|fda.gov|ema.europa.eu|aad.org|nice.org.uk|eadv.org|cochranelibrary.com|who.int|abbvie.com|scholar.google.com|medscape.com|uptodate.com|nea.org|dermatologyjournals.org|thelancet.com|nejm.org|jamanetwork.com|bmj.com|pubmedcentral.ncbi.nlm.nih.gov|drugbank.ca|nih.gov|cdc.gov|wiley.com|springer.com|elsevier.com|ovid.com|embase.com|clinicalkey.com|mjhid.org|dermnetnz.org|dermatologyadvisor.com|allergyasthmanetwork.org|eurekalert.org|bioportfolio.com|pharmaceutical-technology.com|healio.com|reachmd.com|medpagetoday.com|webmd.com|drugs.com|rxlist.com|goodrx.com|mims.com|emc.medicines.org.uk|guidelines.gov|bmjopen.bmj.com|cochrane.org|waset.org|researchgate.net|academia.edu|sciencedirect.com|nature.com|frontiersin.org|plos.org|jamanetwork.com|dermatologytimes.com|dermatologyadvisor.com|eczemamatters.com|allergyuk.org|eczemaassociation.org|dermreview.com|dermatology-world.org|dermatology-life.com|dermhelp.org|dermatology-ontario.com|dermatology-society.org|dermatology-uk.net|dermnet.com|dermnetnz.org|dermnetus.com|dermnetau.com|dermatologychannel.org|dermatologyfocus.com|dermatologyreviews.org|dermatologyupdates.com|dermatologyworldnews.com|dermatologyweekly.com|dermatologytoday.com|dermatologynews.com|dermatologyinsider.com|dermatologyreporter.com|dermatologymagazine.com|dermatologyjournal.net|dermatologyresearch.org|dermatologyclinicaltrials.org|dermatologyevidence.com|dermatologyguidelines.org|dermatologyreviews.net|dermatologyupdates.net|dermatologyworld.org|dermatologyadvisory.com|dermatologyconsultant.com|dermatologyfocus.net|dermatologyinsights.com|dermatologyinteractive.com|dermatologyonline.com|dermatologypro.com|dermatologyspecialist.com|dermatologystudent.com|dermatologyupdate.org|dermatologyworldnews.org|dermatologyweek.com|dermatologyweekly.org|dermatologytoday.net|dermatologynews.org|dermatologyreporter.net|dermatologymagazine.net|dermatologyjournal.org|dermatologyresearch.net|dermatologyclinicaltrials.net|dermatologyevidence.net|dermatologyguidelines.net|dermatologyreviews.org|dermatologyupdates.org|dermatologyworld.org|dermatologyadvisory.net|dermatologyconsultant.net|dermatologyfocus.org|dermatologyinsights.net|dermatologyinteractive.net|dermatologyonline.net|dermatologypro.net|dermatologyspecialist.net|dermatologystudent.net|dermatologyupdate.com|dermatologyworldnews.com|dermatologyweek.net|dermatologyweekly.com|dermatologytoday.org|dermatologynews.net|dermatologyreporter.com|dermatologymagazine.org|dermatologyjournal.com|dermatologyresearch.com|dermatologyclinicaltrials.com|dermatologyevidence.com|dermatologyguidelines.com|dermatologyreviews.com|dermatologyupdates.com|dermatologyworld.com|dermatologyadvisory.org|dermatologyconsultant.org|dermatologyfocus.com|dermatologyinsights.org|dermatologyinteractive.org|dermatologyonline.org|dermatologypro.org|dermatologyspecialist.org|dermatologystudent.org|dermatologyupdate.net|dermatologyworldnews.net|dermatologyweek.org|dermatologyweekly.net|dermatologytoday.com|dermatologynews.com|dermatologyreporter.org|dermatologymagazine.com|dermatologyjournal.net|dermatologyresearch.net|dermatologyclinicaltrials.net|dermatologyevidence.net|dermatologyguidelines.net");
        param.put("freshness", "oneYear");
        param.put("count", 10);

        try (HttpResponse response = HttpRequest.post(SEARCH_API_URL)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(new Gson().toJson(param))
                .execute()) {
            JSONObject jsonObject = JSONUtil.parseObj(response.body());
            JSONObject data = jsonObject.getJSONObject("data");

            if (data != null) {
                List<JSONObject> searchResults = new ArrayList<>();
                JSONObject images = data.getJSONObject("images");
                if (images != null) {
                    JSONArray valueArray = images.getJSONArray("value");
                    if (valueArray != null) {
                        List<JSONObject> searchImages = valueArray.stream().map(obj -> {
                            // 正确处理类型转换：将LinkedHashMap转换为JSONObject
                            JSONObject tmpJSONObject = JSONUtil.parseObj(obj.toString());
                            tmpJSONObject.remove("webSearchUrl");
                            tmpJSONObject.remove("name");
                            tmpJSONObject.remove("hostPageUrl");
                            tmpJSONObject.remove("contentSize");
                            tmpJSONObject.remove("encodingFormat");
                            tmpJSONObject.remove("hostPageDisplayUrl");
                            tmpJSONObject.remove("datePublished");
                            tmpJSONObject.remove("thumbnail");
                            return tmpJSONObject;
                        }).collect(Collectors.toList());
                        searchResults.addAll(searchImages);
                    }
                }
                
                JSONObject webPages = data.getJSONObject("webPages");
                if (webPages != null) {
                    JSONArray valueArray = webPages.getJSONArray("value");
                    if (valueArray != null) {
                        List<JSONObject> searchContent = valueArray.stream().map(obj -> {
                            // 正确处理类型转换：将LinkedHashMap转换为JSONObject
                            JSONObject tmpJSONObject = JSONUtil.parseObj(obj.toString());
                            tmpJSONObject.remove("displayUrl");
                            tmpJSONObject.remove("snippet");
                            tmpJSONObject.remove("datePublished");
                            tmpJSONObject.remove("cachedPageUrl");
                            tmpJSONObject.remove("language");
                            tmpJSONObject.remove("isFamilyFriendly");
                            tmpJSONObject.remove("isNavigational");
                            return tmpJSONObject;
                        }).collect(Collectors.toList());
                        searchResults.addAll(searchContent);
                    }
                }

                return searchResults;


//                System.out.println("=" .repeat(80));
//                System.out.println("=== 文献链接打印结束 ===");
//                System.out.println("=" .repeat(80) + "\n");


//                return result;
            }            
            return null;
        } catch (Exception e) {
//            return "Error searching Baidu: " + e.getMessage();
            return null;
        }
    }
}
