package com.yiming.aiagentproject.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.session.SessionRepository;
import org.springframework.session.web.http.SessionRepositoryFilter;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SessionDebugRunner implements ApplicationRunner {

    @Autowired(required = false)
    private SessionRepository<?> sessionRepository;

    @Autowired(required = false)
    private SessionRepositoryFilter<?> sessionRepositoryFilter;

    @Autowired(required = false)
    private RedisConnectionFactory redisConnectionFactory;

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void run(ApplicationArguments args) {
        log.info("=== Spring Session 诊断 ===");
        log.info("RedisConnectionFactory  = {}", redisConnectionFactory);
        log.info("StringRedisTemplate     = {}", stringRedisTemplate);
        log.info("SessionRepository       = {}", sessionRepository);
        log.info("SessionRepositoryFilter = {}", sessionRepositoryFilter);
        if (stringRedisTemplate != null) {
            try {
                stringRedisTemplate.opsForValue().set("session-debug:ping", "ok");
                log.info("Redis ping 写入成功");
            } catch (Exception e) {
                log.error("Redis ping 写入失败", e);
            }
        }
        log.info("=== 诊断结束 ===");
    }
}
