package com.yiming.aiagentproject.config;

import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Redis 持久化配置
 */
@Configuration
@ConfigurationProperties(prefix = "spring.data.redis")
@Data
public class RedisChatMemoryStoreConfig {
    private static final String DEFAULT_REDIS_USER = "default";

    private String host;
    private Integer port;
    private String username;
    private String password;
    private Long ttl;

    @Bean
    public RedisChatMemoryStore redisChatMemoryStore() {
        RedisChatMemoryStore.Builder builder = RedisChatMemoryStore.builder()
                .host(host)
                .port(port)
                .ttl(ttl);
        String redisUser = resolveRedisUser();
        if (redisUser != null) {
            builder.user(redisUser).password(password);
        }
        return builder.build();
    }

    String resolveRedisUser() {
        if (!StringUtils.hasText(password)) {
            return null;
        }
        if (StringUtils.hasText(username)) {
            return username;
        }
        return DEFAULT_REDIS_USER;
    }
}
