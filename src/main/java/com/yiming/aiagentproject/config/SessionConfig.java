package com.yiming.aiagentproject.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

/**
 * 显式启用 Spring Session + Redis。
 * Spring Boot 4 的 session 自动装配在当前组合下没有生成 SessionRepository，
 * 用注解强制装配 RedisSessionRepository。
 * maxInactiveIntervalInSeconds = 30 天，对齐 cookie 的 max-age。
 */
@Configuration
@EnableRedisHttpSession(maxInactiveIntervalInSeconds = 2592000)
public class SessionConfig {
}
