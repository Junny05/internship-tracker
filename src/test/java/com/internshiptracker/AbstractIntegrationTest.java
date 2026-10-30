package com.internshiptracker;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * One Postgres container for the whole test JVM run, not one per test class.
 * We start it ourselves in a static initializer instead of using {@code @Container}/
 * {@code @Testcontainers}, which would stop (and restart) the container at the end of
 * every test class - costing a few seconds of container startup per class for no benefit,
 * since none of our tests need an isolated database between classes.
 * Testcontainers' Ryuk sidecar still reaps this container when the JVM exits.
 */
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
