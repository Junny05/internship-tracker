package com.internshiptracker.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Optional;

@Component
public class AdzunaJobListingClient implements JobListingClient {

    private static final Logger log = LoggerFactory.getLogger(AdzunaJobListingClient.class);
    private static final String SEARCH_URL_TEMPLATE = "https://api.adzuna.com/v1/api/jobs/{country}/search/1";

    private final RestClient restClient;
    private final String appId;
    private final String appKey;
    private final String country;

    public AdzunaJobListingClient(
            RestClient restClient,
            @Value("${app.adzuna.app-id}") String appId,
            @Value("${app.adzuna.app-key}") String appKey,
            @Value("${app.adzuna.country}") String country) {
        this.restClient = restClient;
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

        String uri = UriComponentsBuilder.fromHttpUrl(SEARCH_URL_TEMPLATE)
                .queryParam("app_id", appId)
                .queryParam("app_key", appKey)
                .queryParam("results_per_page", 20)
                .queryParam("what", keywords)
                .queryParam("content-type", "application/json")
                .queryParamIfPresent("where", Optional.ofNullable(location).filter(l -> !l.isBlank()))
                .buildAndExpand(country)
                .toUriString();

        try {
            AdzunaSearchResponse response = restClient.get().uri(uri).retrieve().body(AdzunaSearchResponse.class);

            if (response == null || response.results() == null) {
                return List.of();
            }

            return response.results().stream()
                    .filter(job -> job.id() != null)
                    .map(this::toJobListing)
                    .toList();
        } catch (Exception e) {
            log.error("Adzuna search failed for keywords='{}' location='{}'", keywords, location, e);
            return List.of();
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
