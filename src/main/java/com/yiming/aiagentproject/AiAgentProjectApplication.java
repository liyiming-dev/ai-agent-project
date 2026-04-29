package com.yiming.aiagentproject;

import dev.langchain4j.community.store.embedding.redis.spring.RedisEmbeddingStoreAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@SpringBootApplication(exclude = {RedisEmbeddingStoreAutoConfiguration.class})

@MapperScan("com.yiming.aiagentproject.mapper")
@EnableAspectJAutoProxy
@EnableCaching
public class AiAgentProjectApplication {

    public static void main(String[] args) {

        SpringApplication.run(AiAgentProjectApplication.class, args);
    }

}
