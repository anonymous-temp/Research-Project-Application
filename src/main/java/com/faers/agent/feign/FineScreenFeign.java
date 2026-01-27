package com.faers.agent.feign;

import com.alibaba.fastjson.JSONObject;
import com.faers.agent.pojo.MongoLiterature;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;

@Component
@FeignClient(name = "fine-screen")
public interface FineScreenFeign {

    @GetMapping("/FineScreenController/paper")
    MongoLiterature paper(@RequestParam(name = "id") String id);

//    @GetMapping("/FineScreenController/clinicalTrials")
//    ClinicalTrialRegistration clinicalTrials(@RequestParam(name = "id") String id);

    @PostMapping("/FineScreenController/screen")
    JSONObject screen(@RequestBody JSONObject body);

    @PostMapping("/FineScreenController/deepl")
    String deepl(@RequestBody JSONObject dataJson);

    @PostMapping("/FineScreenController/baidu")
    String baidu(@RequestBody JSONObject dataJson);

//    /**指南适配检索式*/
//    @PostMapping("/FineScreenController/formula")
//    String formula(@RequestBody ScreenRequest screenRequest, @RequestParam("type")  Integer type);

    /**
     * 添加历史记录到广西精准
     */
    @PostMapping("/FineScreenController/addGHistoryForLcdp")
    void addGHistoryForLcdp(@RequestBody JSONObject body, @RequestHeader String token);
}
