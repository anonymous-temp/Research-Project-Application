// com/faers/agent/agent/context/OnContextChangeListener.java
package com.faers.agent.agent.context;

/**
 * 上下文变化监听器：AnalysisContext 变化时触发回调
 */
@FunctionalInterface
public interface OnContextChangeListener {
    void onContextChanged(); // 变化时调用的方法
}