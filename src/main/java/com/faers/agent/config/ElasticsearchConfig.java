package com.faers.agent.config;

import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.RestClients;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;

import java.time.Duration;


@Configuration("agent1ElasticsearchConfig")
public class ElasticsearchConfig {

    @Bean(name = "agent1ElasticsearchClient")
    public RestHighLevelClient elasticsearchClient() {
        // 使用application-dev.yml中的实际Elasticsearch连接配置
        // 修复：将超时时间从3600秒（1小时）改为合理的10秒
        ClientConfiguration clientConfiguration = ClientConfiguration.builder()
                .connectedTo("es-cn-8t84ft4ee0003tib9.public.elasticsearch.aliyuncs.com:9200")
                .withBasicAuth("elastic", "Lxyl123456")
                .withConnectTimeout(Duration.ofSeconds(10))  // 连接超时：10秒
                .withSocketTimeout(Duration.ofSeconds(30))   // 读取超时：30秒
                .build();
        
        return RestClients.create(clientConfiguration).rest();
    }

    @Bean(name = "agent1ElasticsearchRestTemplate")
    public ElasticsearchRestTemplate elasticsearchRestTemplate() {
        return new ElasticsearchRestTemplate(elasticsearchClient());
    }
}