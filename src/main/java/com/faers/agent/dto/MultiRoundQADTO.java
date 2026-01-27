package com.faers.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MultiRoundQADTO {
    /**
     * 会话检索id
     */
    private String screenId;
    /**
     * 当前回答id
     */
    private String id;
    /**
     * 检索内容
     */
    private String query;
    /**
     * 是否引用参考信息，默认true
     */
    private Boolean isReference = true;
    /**
     * 用户上传的文件id
     */
    private List<String> fileIds;
    /**
     * 用于websocket发送信息
     */
    private String senderId;
    private String targetClientId;
    private String userId;
}
