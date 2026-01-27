package com.faers.agent.agent.client;

public interface WebSocketMessageSender {
    void sendMessage(String message);

    void sendMessage(String targetClientId, String content, String id,
                     String parentId,
                     String userId);

    void sendMessageTable(String targetClientId, String content, String id,
                          String parentId,
                          String userId);
}
