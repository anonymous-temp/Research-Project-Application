package com.faers.agent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;

/**
 * mongo多数据源配置类
 * @author zgm
 */
@Configuration
public class MultipleMongoConfig {
    @Value("${spring.data.mongodb.evimed.uri}")
    private String evimedUri;
//    @Value("${spring.data.mongodb.uri}")
//    private String evimedReleaseUri;

//    @Primary
//    @Bean(name = "evimedMongoTemplate")
//    public MongoTemplate evimedMongoTemplate() {
//        SimpleMongoClientDatabaseFactory simpleMongoClientDbFactory = new SimpleMongoClientDatabaseFactory(evimedReleaseUri);
//        return new MongoTemplate(simpleMongoClientDbFactory);
//    }

//     添加默认的mongoTemplate bean以满足SessionRepository的需求
    @Bean
    @Primary
    public MongoTemplate mongoTemplate() {
        SimpleMongoClientDatabaseFactory simpleMongoClientDbFactory = new SimpleMongoClientDatabaseFactory(evimedUri);
        return new MongoTemplate(simpleMongoClientDbFactory);
    }

    /*@Bean(name = "evimedReleaseMongoTemplate")
    public MongoTemplate evimedReleaseMongoTemplate() {
        SimpleMongoClientDatabaseFactory simpleMongoClientDbFactory = new SimpleMongoClientDatabaseFactory(evimedReleaseUri);
        return new MongoTemplate(simpleMongoClientDbFactory);
    }*/
}