package com.faers.agent.dto.responseDto;

import lombok.Data;

/**
 * 流式响应实体类
 * 对应JSON结构：{"type": "stream", "data": {...}, "class": "chat"}
 */
@Data
public class ResponseContent {


    /**
     * 数据体内部实体类
     */

        /**
         * 数据类型（固定值：text）
         */
        private String type;

        /**
         * 内容增量（如：正在）
         */
        private String delta;

        /**
         * 是否处理中
         */
        private boolean inprogress;

        /**
         * 调用ID（可为null）
         */
        private String callid;

        /**
         * 参数（可为null）
         */
        private Object argument;

        /**
         * 前端展示内容
         */
        private String front_display;


        private String isFinished;

        private Boolean highlightType;



}