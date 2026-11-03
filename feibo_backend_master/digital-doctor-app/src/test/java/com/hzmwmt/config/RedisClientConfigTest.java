package com.hzmwmt.config;

import org.junit.jupiter.api.Test;
import org.redisson.config.SingleServerConfig;

import static org.assertj.core.api.Assertions.assertThat;

class RedisClientConfigTest {

    private final RedisClientConfig redisClientConfig = new RedisClientConfig();

    @Test
    void omitsAuthenticationWhenCredentialsAreBlank() {
        RedisClientConfigProperties properties = properties("", " ");

        SingleServerConfig server = redisClientConfig.createConfig(properties).useSingleServer();

        assertThat(server.getUsername()).isNull();
        assertThat(server.getPassword()).isNull();
    }

    @Test
    void configuresAuthenticationWhenCredentialsArePresent() {
        RedisClientConfigProperties properties = properties("redis-user", "redis-password");

        SingleServerConfig server = redisClientConfig.createConfig(properties).useSingleServer();

        assertThat(server.getUsername()).isEqualTo("redis-user");
        assertThat(server.getPassword()).isEqualTo("redis-password");
    }

    private RedisClientConfigProperties properties(String username, String password) {
        RedisClientConfigProperties properties = new RedisClientConfigProperties();
        properties.setHost("127.0.0.1");
        properties.setPort(6379);
        properties.setUserName(username);
        properties.setPassword(password);
        properties.setPoolSize(4);
        properties.setMinIdleSize(1);
        return properties;
    }
}
