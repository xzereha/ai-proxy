package com.example.ai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.netty.channel.ChannelOption;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import reactor.netty.http.client.HttpClient;

@Configuration
public class AppConfig {

    @Bean
    public WebClient webClient(
            @Value("${openai.connect-timeout-ms}") int connectTimeoutMs,
            @Value("${openai.response-timeout-seconds}") int responseTimeoutSeconds) {
        var httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
                .responseTimeout(Duration.ofSeconds(responseTimeoutSeconds));
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public CaffeineCacheManager cacheManager(
            @Value("${spring.cache.expire-after-write-minutes}") long expireAfterWriteMinutes,
            @Value("${spring.cache.maximum-size}") int maximumSize) {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setAsyncCacheMode(true);
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(expireAfterWriteMinutes, TimeUnit.MINUTES)
                .maximumSize(maximumSize));
        return cacheManager;
    }
}
