package com.internshiptracker.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdzunaJobListingClientTest {

    private WireMockServer adzuna;
    private AdzunaJobListingClient client;

    @BeforeEach
    void setUp() {
        adzuna = new WireMockServer(options().dynamicPort());
        adzuna.start();
        client = new AdzunaJobListingClient(
                RestClient.builder().build(),
                "http://localhost:" + adzuna.port() + "/v1/api",
                "test-app-id",
                "test-app-key",
                "us");
    }

    @AfterEach
    void tearDown() {
        adzuna.stop();
    }

    @Test
    void outageThrowsInsteadOfSilentlyReturningEmptyList() {
        adzuna.stubFor(get(urlPathEqualTo("/v1/api/jobs/us/search/1"))
                .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> client.search("java intern", "Austin"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void legitimateEmptyResultsStillReturnEmptyList() {
        adzuna.stubFor(get(urlPathEqualTo("/v1/api/jobs/us/search/1"))
                .willReturn(okJson("""
                        {"results": []}
                        """)));

        assertThat(client.search("java intern", "Austin")).isEmpty();
    }
}
