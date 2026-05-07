package com.yiming.aiagentproject.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RedisChatMemoryStoreConfigTest {

    @Test
    void resolveRedisUserUsesDefaultWhenOnlyPasswordIsConfigured() {
        RedisChatMemoryStoreConfig config = new RedisChatMemoryStoreConfig();
        config.setPassword("secret");

        assertThat(config.resolveRedisUser()).isEqualTo("default");
    }

    @Test
    void resolveRedisUserUsesConfiguredUsernameWhenPresent() {
        RedisChatMemoryStoreConfig config = new RedisChatMemoryStoreConfig();
        config.setUsername("app-user");
        config.setPassword("secret");

        assertThat(config.resolveRedisUser()).isEqualTo("app-user");
    }

    @Test
    void resolveRedisUserSkipsAuthWhenPasswordIsBlank() {
        RedisChatMemoryStoreConfig config = new RedisChatMemoryStoreConfig();

        assertThat(config.resolveRedisUser()).isNull();
    }
}
