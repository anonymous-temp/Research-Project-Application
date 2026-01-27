package com.faers.agent.model;

import java.io.Serializable;

public class ResponseDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String type;
    private DataDTO data;

    // 无参构造
    public ResponseDTO() {}

    // 全参构造
    public ResponseDTO(String type, DataDTO data) {
        this.type = type;
        this.data = data;
    }

    // getter/setter
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public DataDTO getData() {
        return data;
    }

    public void setData(DataDTO data) {
        this.data = data;
    }

    // 嵌套DataDTO
    public static class DataDTO implements Serializable {
        private static final long serialVersionUID = 1L;

        private String type;
        private String delta;
        private Boolean inprogress;
        private Object callid;
        private Object argument;

        // 无参构造
        public DataDTO() {}

        // 全参构造
        public DataDTO(String type, String delta, Boolean inprogress, Object callid, Object argument) {
            this.type = type;
            this.delta = delta;
            this.inprogress = inprogress;
            this.callid = callid;
            this.argument = argument;
        }

        // getter/setter
        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getDelta() {
            return delta;
        }

        public void setDelta(String delta) {
            this.delta = delta;
        }

        public Boolean getInprogress() {
            return inprogress;
        }

        public void setInprogress(Boolean inprogress) {
            this.inprogress = inprogress;
        }

        public Object getCallid() {
            return callid;
        }

        public void setCallid(Object callid) {
            this.callid = callid;
        }

        public Object getArgument() {
            return argument;
        }

        public void setArgument(Object argument) {
            this.argument = argument;
        }
    }
}