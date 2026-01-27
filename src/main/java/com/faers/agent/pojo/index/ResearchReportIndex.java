package com.faers.agent.pojo.index;

import com.faers.agent.pojo.annotation.EsDocument;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 研报索引
 * @author zgm
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@EsDocument(index = "research_report_index")
public class ResearchReportIndex {
    /**
     * 研报唯一id
     */
    private String dbId;
    /**
     * 研报标题
     */
    private String title;
    /**
     * 拆分的文本块
     */
    private String text;
}
