package com.faers.agent.core.agent;

import com.faers.agent.agent.context.AnalysisContext;
import com.faers.agent.pojo.StreamDataCallback;

/**
 * ProposalAgent - Base interface for multi-agent proposal generation
 *
 * This interface defines the contract for all agents in the proposal generation pipeline.
 * Each agent specializes in a specific task within the pipeline:
 *
 * - ResearcherAgent: Evidence collection and EvidencePack building
 * - PlannerAgent: Logical blueprint creation
 * - WriterAgent: Proposal draft generation
 * - CriticAgent: Validation and quality assurance
 */
public interface ProposalAgent<I, O> {

    /**
     * Get the unique name of this agent
     */
    String getAgentName();

    /**
     * Get the description of this agent's responsibilities
     */
    String getAgentDescription();

    /**
     * Execute the agent's task
     *
     * @param input The input to process
     * @param context The analysis context
     * @param callback Callback for streaming updates
     * @param traceId Trace ID for logging
     * @return The output of the agent's processing
     */
    O execute(I input, AnalysisContext context, StreamDataCallback callback, String traceId) throws Exception;

    /**
     * Check if the agent can handle the given input
     */
    default boolean canHandle(I input) {
        return input != null;
    }

    /**
     * Get the agent's status
     */
    default AgentStatus getStatus() {
        return AgentStatus.READY;
    }

    /**
     * Agent status enumeration
     */
    enum AgentStatus {
        READY,
        BUSY,
        ERROR,
        DISABLED
    }
}
