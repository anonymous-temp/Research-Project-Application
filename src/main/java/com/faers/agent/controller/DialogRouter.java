package com.faers.agent.controller;

import cn.hutool.core.collection.CollUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.faers.agent.agent.client.NettyWebSocketClient;
import com.faers.agent.agent.context.AnalysisContext;
import com.faers.agent.agent.context.DialogContext;
import com.faers.agent.agent.dialog.DialogManager;
import com.faers.agent.agent.impl.AnalysisOrchestrator;
import com.faers.agent.agent.impl.CompressAgent;
import com.faers.agent.dto.MultiRoundQADTO;
import com.faers.agent.dto.responseDto.Response;
import com.faers.agent.pojo.History;
import com.faers.agent.pojo.StreamDataCallback;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;


@Component
@Slf4j
public class DialogRouter {

    @Autowired
    private AnalysisOrchestrator orchestrator;
    @Autowired
    private NettyWebSocketClient nettyWebSocketClient;
    @Autowired
    private DialogManager dialogManager;
    @Autowired
    private com.faers.agent.exception.GlobalExceptionHandler globalExceptionHandler;
    @Resource
    private MongoTemplate mongoTemplate;

    @EventListener
    @Async  // 添加异步注解
    public void onWebSocketMessage(String message) {
        // 处理WebSocket消息
        JSONObject msg = JSONObject.parseObject(message);
        // 调用业务方法

        String dialogId = msg.getString("senderId");

        String id = msg.getString("id");

        String parentId = msg.getString("parentId");

        String userId = msg.getString("userId");

        String content = msg.getString("content");

        String targetClientId = msg.getString("targetClientId");

        onMessageReceived(dialogId, null, targetClientId, content, id, parentId, userId);

    }
    public void onMessageReceived(
            String dialogId,
            String senderId,
            String targetClientId,
            String content,
            String id,
            String parentId,
            String userId

    ) {
        StringBuilder dialogBuilder = new StringBuilder();
        List<JSONObject> quoteList = new ArrayList<>();
        List<String> subQuestionList = new ArrayList<>();
        MultiRoundQADTO multiRoundQADTO = new MultiRoundQADTO();
        multiRoundQADTO.setId(id);
        multiRoundQADTO.setSenderId(dialogId);
        multiRoundQADTO.setTargetClientId(targetClientId);
        multiRoundQADTO.setUserId(userId);
        multiRoundQADTO.setScreenId(parentId);
        String type = "Stream";
        multiRoundQADTO.setQuery(type);

        log.info("开始处理对话 | dialogId={} | contentLength={} | userId={}",
                dialogId, content.length(), userId);


        StreamDataCallback callback = new StreamDataCallback() {


            @Override
            public void onData(Response chunk) {
                JSONObject message = new JSONObject();
                message.put("type", "text");
                message.put("senderId", targetClientId);
                message.put("targetClientId", dialogId);
                message.put("senderType", "project-application-form");
                message.put("parentId", parentId);
                message.put("id", id);
                message.put("userId", userId);
                message.put("agentType", "project-application-form");
                //chunk转为json
                String jsonObject = JSON.toJSONString(chunk);
                message.put("content", jsonObject);
                nettyWebSocketClient.sendMessage(message.toJSONString());
            }
            @Override
            public void onData(String chunk) {

                try {
                    // 尝试解析JSON
                    JSON.parseObject(chunk);
                    nettyWebSocketClient.sendMessageTable(dialogId, chunk, id, parentId, userId);

                } catch (Exception e) {
                    // 不是JSON格式，作为流式数据处理
                    if (!"".equals(chunk)) {
                        if (!"[END]".equalsIgnoreCase(chunk)) {
                            chunk = chunk.replaceAll("\\[\\[", "[").replaceAll("]]", "]");
                            if (chunk.contains("￥￥")) {
                                chunk = chunk.replaceAll("￥￥", "");
                                List<String> list = JSONArray.parseArray(chunk, String.class);
                                subQuestionList.addAll(list);
                                chunk = "";
                            }
                            // 使用统一的dialogBuilder来累积对话内容
                            toSocket(chunk, multiRoundQADTO, quoteList, dialogBuilder, subQuestionList);
                            String innerStr = chunk.replaceAll("\\[(\\d+)]", "");

                        } else {
                            // 处理结束消息，使用统一的dialogBuilder
                            toSocket("[END]", multiRoundQADTO, quoteList, dialogBuilder, subQuestionList);
                        }
                    }
                }
            }

            @Override
            public void onComplete() {
                System.out.println("主线程：流式数据接收完成");
            }

            @Override
            public void onError(Throwable e) {
                System.err.println("主线程：流式数据错误");
                e.printStackTrace();
            }
        };
        String screenId = multiRoundQADTO.getScreenId();

        List<Message> messages = new ArrayList<>();
        // 基于历史记录及用户上传的文件进行压缩和拼接新的检索query
        CompressAgent compressAgent = new CompressAgent();
        Query mongoQuery = new Query(Criteria.where("parentId").is(screenId));
        mongoQuery.with(Sort.by(Sort.Direction.ASC, "ts"));
        List<History> historyList = mongoTemplate.find(mongoQuery, History.class);

        for (History history : historyList) {
            String historyContent = history.getContent();
            if (StringUtils.isNotBlank(historyContent)) {
                String compressHistory = compressAgent.history(historyContent);
                if (StringUtils.isNotBlank(compressHistory)) {
                    messages.add(new UserMessage(compressHistory));
                }
            }
        }
        try {
            DialogContext ctx = dialogManager.getContext(parentId);
            AnalysisContext context = ctx.getAnalysisContext();
            context.setUserMessage(content);
            context.setTraceId(parentId);



            orchestrator.process(context, id, parentId, userId, callback); // 执行意图识别 → 参数收集 → 分析 → 回复

        } catch (com.faers.agent.exception.LLMInvocationException e) {
            log.error("LLM调用异常 | traceId={} | attempt={}/{} | error={}",
                    e.getAttempt(), e.getMaxAttempts(), e.getMessage(), e);
            try {
                globalExceptionHandler.handleLLMInvocationException(e, callback);
            } catch (Exception ex) {
                log.error("全局异常处理器处理LLM异常失败", ex);
            }
        } catch (Exception e) {
            log.error("路由处理失败", e);
            // 使用全局异常处理器处理其他异常
            try {
                globalExceptionHandler.handleException(e, callback);
            } catch (Exception ex) {
                log.error("全局异常处理器处理失败", ex);
            }
        }
    }
    public void toSocket(String text, MultiRoundQADTO multiRoundQADTO, List<JSONObject> quoteList, StringBuilder builder, List<String> subQuestionList) {
        String senderId = multiRoundQADTO.getSenderId();
        String targetClientId = multiRoundQADTO.getTargetClientId();
        String screenId = multiRoundQADTO.getScreenId();
        String singleRoundId = multiRoundQADTO.getId();
        String userId = multiRoundQADTO.getUserId();


        JSONObject deltaJson = new JSONObject();
        if (CollUtil.isNotEmpty(subQuestionList)) {
            deltaJson.put("subQuestion", subQuestionList);
        }

        if ("[END]".equals(text)) {
            deltaJson.put("isFinished", true);
            // 在设置delta之后再清空builder，确保最后一次发送包含完整内容
            String finalContent = builder.toString();
            deltaJson.put("delta", finalContent);
            deltaJson.put("inprogress", true);

            // 保存历史记录
            try {
                History history = new History();
                history.setUserId(multiRoundQADTO.getUserId());
                history.setInput(multiRoundQADTO.getQuery());
                history.setContent(finalContent); // 使用清空前的完整内容
                history.setParentId(screenId);
                history.setSt(System.currentTimeMillis());

                // 日志记录完整的History对象信息
                log.info("准备保存对话历史 | userId={} | input={} | contentLength={} | parentId={}",
                        history.getUserId(), history.getInput(), history.getContent().length(), history.getParentId());

                // 执行插入操作
                mongoTemplate.insert(history);
                log.info("保存一轮对话历史记录成功 | historyId={}", history.getId());
            } catch (Exception e) {
                log.error("保存对话历史记录失败 | userId={} | parentId={} | error={}",
                        multiRoundQADTO.getUserId(), screenId, e.getMessage(), e);
            }

            // 最后清空builder
            builder.setLength(0);
        } else {
            deltaJson.put("isFinished", false);
            builder.append(text);
            deltaJson.put("delta", builder.toString());
            deltaJson.put("inprogress", true);
        }
        deltaJson.put("quote", quoteList);
        deltaJson.put("type", "text");


        JSONObject dataJson = new JSONObject();
        dataJson.put("clazz", "agent");
        dataJson.put("data", deltaJson);
        dataJson.put("type", "stream");

        JSONObject message = new JSONObject();
        message.put("content", dataJson.toJSONString());
        message.put("type", "text");
        message.put("senderId", targetClientId);
        message.put("targetClientId", senderId);
        message.put("senderType", "project-application-form");
        message.put("parentId", screenId);
        message.put("id", singleRoundId);
        message.put("userId", userId);
        message.put("agentType", "project-application-form");
        nettyWebSocketClient.sendMessage(message.toJSONString());
    }
}
