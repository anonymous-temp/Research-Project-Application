package com.faers.agent.utils;


import com.faers.agent.feign.ScreenFeignBean;
import com.faers.agent.pojo.MongoLiterature;

/**
 * 获取文献详情数据
 * @author zgm
 */
public class PaperUtils {
    /**
     * 根据文献id查询文献信息
     * @param id 文献id
     * @return 文献信息
     */
    public static MongoLiterature paper(String id) {
        return ScreenFeignBean.screenFeign.paper(id);
    }
}
