package com.internshiptracker.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
@EnableScheduling
public class JobAlertConfig {

    @Bean
    public RestClient restClient(
            @Value("${app.adzuna.connect-timeout-ms:3000}") long connectTimeoutMs,
            @Value("${app.adzuna.read-timeout-ms:5000}") long readTimeoutMs) {
        // Without an explicit timeout, a hung Adzuna connection blocks a poll indefinitely.
        var settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .withReadTimeout(Duration.ofMillis(readTimeoutMs));
        return RestClient.builder()
                .requestFactory(ClientHttpRequestFactories.get(settings))
                .build();
    }
}
