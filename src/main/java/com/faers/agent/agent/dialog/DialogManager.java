// com/faers/agent/dialog/DialogManager.java
package com.faers.agent.agent.dialog;

import com.faers.agent.agent.context.DialogContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 对话管理器
 * 功能：
 * - 根据 dialogId 获取或创建 DialogContext
 * - 自动清理过期对话（30分钟无活动）
 * - 线程安全，支持高并发
 */
@Component
public class DialogManager {

    private final Map<String, DialogContext> contexts = new ConcurrentHashMap<>();

    // 过期时间：30分钟
    private final long EXPIRE_TIME_MILLIS =6 * 60 * 60 * 1000;

    /**
     * 获取或创建新的对话上下文
     * @param dialogId 对话ID（唯一标识）
     * @return 该对话的上下文
     */
    public DialogContext getContext(String dialogId) {
        return contexts.computeIfAbsent(dialogId, id -> new DialogContext(id));
    }





    /**
     * 强制移除某个对话（如用户退出）
     */
    public void removeContext(String dialogId) {
        contexts.remove(dialogId);
    }

    /**
     * 定时任务：每分钟清理一次过期对话
     */
    @Scheduled(fixedRate = 60_000)
    public void cleanupExpired() {
        long now = System.currentTimeMillis();
        contexts.entrySet().removeIf(entry -> {
            DialogContext ctx = entry.getValue();
            return now - ctx.getLastActiveTime() > EXPIRE_TIME_MILLIS;
        });
    }

    /**
     * 获取当前活跃对话数（监控用）
     */
    public int getActiveCount() {
        return contexts.size();
    }
}
