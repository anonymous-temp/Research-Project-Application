package com.faers.agent.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ChatClient配置类
 * 提供ChatClient Bean供其他服务使用
 */
@Configuration
public class ChatClientConfig {

    /**
     * 创建ChatClient Bean
     * Spring AI会自动配置ChatClient.Builder，我们只需要使用它来构建ChatClient实例
     *
     * @param builder ChatClient.Builder（由Spring AI自动注入）
     * @return ChatClient实例
     */
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}



