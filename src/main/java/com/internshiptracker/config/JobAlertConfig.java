package com.internshiptracker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

@Configuration
@EnableScheduling
public class JobAlertConfig {

    @Bean
    public RestClient restClient() {
        return RestClient.builder().build();
    }
}
