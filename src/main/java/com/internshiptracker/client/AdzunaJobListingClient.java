package com.internshiptracker.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Optional;

@Component
public class AdzunaJobListingClient implements JobListingClient {

    private static final Logger log = LoggerFactory.getLogger(AdzunaJobListingClient.class);
    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_BACKOFF_MS = 200;

    private final RestClient restClient;
    private final String baseUrl;
    private final String appId;
    private final String appKey;
    private final String country;

    public AdzunaJobListingClient(
            RestClient restClient,
            @Value("${app.adzuna.base-url}") String baseUrl,
            @Value("${app.adzuna.app-id}") String appId,
            @Value("${app.adzuna.app-key}") String appKey,
            @Value("${app.adzuna.country}") String country) {
        this.restClient = restClient;
        this.baseUrl = baseUrl;
        this.appId = appId;
        this.appKey = appKey;
        this.country = country;
    }

    @Override
    public List<JobListing> search(String keywords, String location) {
        if (appId == null || appId.isBlank() || appKey == null || appKey.isBlank()) {
            log.warn("Adzuna API credentials are not configured (ADZUNA_APP_ID / ADZUNA_APP_KEY) - skipping search");
            return List.of();
        }

        String uri = UriComponentsBuilder.fromHttpUrl(baseUrl + "/jobs/{country}/search/1")
                .queryParam("app_id", appId)
                .queryParam("app_key", appKey)
                .queryParam("results_per_page", 20)
                .queryParam("what", keywords)
                .queryParam("content-type", "application/json")
                .queryParamIfPresent("where", Optional.ofNullable(location).filter(l -> !l.isBlank()))
                .buildAndExpand(country)
                .toUriString();

        AdzunaSearchResponse response = fetchWithRetry(uri, keywords, location);

        if (response == null || response.results() == null) {
            return List.of();
        }

        return response.results().stream()
                .filter(job -> job.id() != null)
                .map(this::toJobListing)
                .toList();
    }

    // Retries only transient failures (network issues, timeouts, 5xx) - a 4xx
    // means something about the request itself is wrong and won't fix itself
    // on retry. Once attempts are exhausted the exception propagates instead
    // of being swallowed into an empty list, so the caller (JobAlertService,
    // via its per-search catch in pollAllSavedSearches) can tell "Adzuna is
    // down" apart from "no new listings" instead of treating them the same.
    private AdzunaSearchResponse fetchWithRetry(String uri, String keywords, String location) {
        RuntimeException lastFailure = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return restClient.get().uri(uri).retrieve().body(AdzunaSearchResponse.class);
            } catch (ResourceAccessException | HttpServerErrorException e) {
                lastFailure = e;
                log.warn("Adzuna search attempt {}/{} failed for keywords='{}' location='{}': {}",
                        attempt, MAX_ATTEMPTS, keywords, location, e.getMessage());
                if (attempt < MAX_ATTEMPTS) {
                    sleepBeforeRetry();
                }
            }
        }

        throw lastFailure;
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(RETRY_BACKOFF_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while retrying Adzuna search", e);
        }
    }

    private JobListing toJobListing(AdzunaJob job) {
        return new JobListing(
                job.id(),
                job.title(),
                job.company() != null ? job.company().displayName() : "Unknown company",
                job.location() != null ? job.location().displayName() : null,
                job.redirectUrl());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AdzunaSearchResponse(List<AdzunaJob> results) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AdzunaJob(
            String id,
            String title,
            AdzunaCompany company,
            AdzunaLocation location,
            @JsonProperty("redirect_url") String redirectUrl) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AdzunaCompany(@JsonProperty("display_name") String displayName) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AdzunaLocation(@JsonProperty("display_name") String displayName) {
    }
}
