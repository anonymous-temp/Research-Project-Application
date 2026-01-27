package com.faers.agent.dynamic.config;

import com.faers.agent.dynamic.actions.*;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;


@Configuration
public class DynamicActionsConfig {
    @Autowired
    private ActionRegistry registry;
    @Autowired
    private WebSearchAction webSearchAction;
    @Autowired
    private OutlineGenerateAction outlineGenerateAction;
    @Autowired
    private LegacySectionWriteAction legacySectionWriteAction;
    @Autowired
    private MergeDocumentAction mergeDocumentAction;
    @Autowired
    private GenericLLMAction genericLLMAction;

    @PostConstruct
    public void init() {
        registry.register("web_search", webSearchAction);
        registry.register("outline_generate", outlineGenerateAction);
        registry.register("section_write_legacy", legacySectionWriteAction);
        registry.register("merge_document", mergeDocumentAction);
        registry.register("llm_task", genericLLMAction);
    }
}
