package com.faers.agent.dto.responseDto;

import com.faers.agent.agent.context.AnalysisContext;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Response<T> {
    /**
     * 响应类型（固定值：stream）
     */
    private String type;

    /**
     * 数据体
     */
    private T data;

    /**
     * 消息分类（固定值：chat）
     */
    private String clazz;



    public static Response  ResponseContentChatBuilder(String  delta,String clazz) {
        Response<ResponseContent> responseContent = new Response<>();
        responseContent.setType("stream");
        if (clazz==null){
            clazz="agent";
        }
            responseContent.setClazz(clazz);

        responseContent.setData(new ResponseContent());
        responseContent.getData().setType("text");
        responseContent.getData().setInprogress(true);
        responseContent.getData().setDelta(delta);
        responseContent.getData().setArgument(null);
        responseContent.getData().setCallid(null);
//        responseContent.getData().setFront_display(delta);
        return responseContent;
    }


    public static Response  ResponseReportChatBuilder(String  delta,String clazz) {
        Response<ResponseContent> responseContent = new Response<>();
        responseContent.setType("report_writing_stream");
        if (clazz==null){
            clazz="agent";
        }
        responseContent.setClazz(clazz);

        responseContent.setData(new ResponseContent());
        responseContent.getData().setType("text");
        responseContent.getData().setInprogress(true);
        responseContent.getData().setDelta(delta);
        responseContent.getData().setArgument(null);
        responseContent.getData().setCallid(null);
        responseContent.getData().setFront_display(delta);
        return responseContent;
    }


    public static Response  ResponseContentChatBuilderNoStream(String  delta) {
        Response<ResponseContent> responseContent = new Response<>();
        responseContent.setType("raw");
        responseContent.setClazz("agent");
        responseContent.setData(new ResponseContent());
        responseContent.getData().setType("text");
        responseContent.getData().setInprogress(true);
        responseContent.getData().setDelta(delta);
        responseContent.getData().setArgument(null);
        responseContent.getData().setCallid(null);
        responseContent.getData().setFront_display(delta);
        return responseContent;
    }

    public static Response  newBuilder(String  name) {
        Response<NewItem> response = new Response<>();
        response.setType("new");
        response.setClazz("agent");
        response.setData(new NewItem());
        response.getData().setType("new");
        response.getData().setName(name);
        return response;

    }


    public static Response  itemBuilder(String  analysis, List<String> todo, List<String> todo_details) {
        Response<Item<ItemData>> response = new Response<>();
        response.setType("orchestra");
        response.setClazz("agent");
        response.setData(new Item());
        response.getData().setType("plan");
        response.getData().setItem(new ItemData());
        response.getData().getItem().setAnalysis(analysis);
        response.getData().getItem().setTodo(todo);
        response.getData().getItem().setTodo_details(todo_details);
        return response;
    }

    public static Response  itemBuilder2(String  analysis, List<String> todo, List<String> todo_details) {
        Response<Item<ItemData>> response = new Response<>();
        response.setType("previewPlan");
        response.setClazz("agent");
        response.setData(new Item());
        response.getData().setType("plan");
        response.getData().setItem(new ItemData());
        response.getData().getItem().setAnalysis(analysis);
        response.getData().getItem().setTodo(todo);
        response.getData().getItem().setTodo_details(todo_details);
        return response;
    }

    public static Response  itemInfoBuilder(List<String> title, List<String> status) {
        Response<Item<List<ItemInfo>>> response = new Response<>();
        response.setType("status");
        response.setClazz("agent");
        response.setData(new Item<List<ItemInfo>>());
        response.getData().setType("task_status");
        response.getData().setItem(new ArrayList<ItemInfo>());
        for (int i = 0; i < title.size(); i++) {
            ItemInfo itemInfo = new ItemInfo();
            itemInfo.setStatus(status.get(i));
            itemInfo.setTitle(title.get(i));
            response.getData().getItem().add(itemInfo);
        }
        return response;
    }

    //流程显示
    public static Response  flowBuilder(String delta,String  front_display,String callId,String argument) {

        Response<ResponseContent> response = new Response<>();
        response.setType("raw");
        response.setData(new ResponseContent());
        response.getData().setType("tool_call");
        response.getData().setArgument("");
        response.getData().setCallid(callId);
        response.getData().setFront_display(front_display);
        response.getData().setDelta(delta);
        response.getData().setInprogress(true);
        response.setClazz("agent");
        return response;
    }

    //流程结果返回
    public static Response  flowResultBuilder(String delta,String callId) {
        Response<ResponseContent> response = new Response<>();
        response.setType("raw");
        response.setData(new ResponseContent());
        response.getData().setType("tool_call_output");
        response.getData().setArgument(null);
        response.getData().setCallid(callId);
        response.getData().setDelta(delta);
        response.getData().setInprogress(false);
        response.getData().setFront_display(delta);
        response.setClazz("agent");
        return response;
    }


    public static Response<ImageData>  ImageBuilder(String url) {
        Response<ImageData> response = new Response<>();
        response.setType("image");
        response.setData(new ImageData());
        response.getData().setImageUrl(url);
        response.getData().setName("image");
        response.setClazz("agent");
        return response;
    }

    //最终返回
    public static Response<FinishData>  finishBuilder(String url,AnalysisContext context) {
        String topicName = getTopicNameFromContext(context);
        if (topicName == null || topicName.isEmpty()) {
            throw new IllegalArgumentException("课题名称不能为空");
        }

        Response<FinishData> response = new Response<>();
        response.setType("finish");
        response.setData(new FinishData());
//        response.getData().setUrl(url);
        response.getData().setMd(url);
        response.getData().setName(topicName+"报告");
        response.getData().setPdf("");
        response.setClazz("agent");
        return response;
    }



    /**
     * 从上下文中获取课题名称
     * @param context 分析上下文
     * @return 课题名称
     */
    private static String getTopicNameFromContext(AnalysisContext context) {
        // 优先从queryParams中获取
        if (context.getQueryParams() != null && context.getQueryParams().containsKey("topicName")) {
            return context.getQueryParams().get("topicName").toString();
        }
        // 其次从userMessage中提取
        return context.getUserMessage();
    }
}
