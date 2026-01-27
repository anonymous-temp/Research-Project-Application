package com.faers.agent.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 历史记录存储
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Document("ai_manus_history_list")
public class History {
    @Id
    private String id;
    /**
     * 用户id
     */
    private String userId;
    /**
     * 用户输入内容
     */
    private String input;
    /**
     * 历史数据内容
     */
    private String content;
    /**
     * 父类id 绑定当前会话全部历史信息 顶级为-1
     */
    private String parentId;
    /**
     * 存储时间戳
     */
    private Long st;
}
