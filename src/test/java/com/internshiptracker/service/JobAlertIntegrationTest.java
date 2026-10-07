package com.internshiptracker.service;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.internshiptracker.entity.SavedSearch;
import com.internshiptracker.entity.User;
import com.internshiptracker.repository.SavedSearchRepository;
import com.internshiptracker.repository.SeenListingRepository;
import com.internshiptracker.repository.UserRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
class JobAlertIntegrationTest {

    private static final WireMockServer ADZUNA = new WireMockServer(options().dynamicPort());

    @Autowired
    private JobAlertService jobAlertService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SavedSearchRepository savedSearchRepository;

    @Autowired
    private SeenListingRepository seenListingRepository;

    @MockBean
    private JavaMailSender mailSender;

    @AfterAll
    static void stopAdzuna() {
        ADZUNA.stop();
    }

    // Runs before the context starts, so the server must be up here (not in @BeforeAll) to read its port
    @DynamicPropertySource
    static void adzunaProperties(DynamicPropertyRegistry registry) {
        ADZUNA.start();
        registry.add("app.adzuna.base-url", () -> "http://localhost:" + ADZUNA.port() + "/v1/api");
        registry.add("app.adzuna.app-id", () -> "test-app-id");
        registry.add("app.adzuna.app-key", () -> "test-app-key");
    }

    @AfterEach
    void cleanUp() {
        ADZUNA.resetAll();
        seenListingRepository.deleteAll();
        savedSearchRepository.deleteAll();
        userRepository.deleteAll();
        reset(mailSender);
    }

    @Test
    void newListingsEmailOnceAndSecondRunSendsNothing() {
        User user = userRepository.save(User.builder()
                .email("alerts-" + UUID.randomUUID() + "@example.com")
                .passwordHash("not-used")
                .build());
        savedSearchRepository.save(SavedSearch.builder()
                .user(user)
                .keywords("java intern")
                .location("Austin")
                .build());

        ADZUNA.stubFor(get(urlPathEqualTo("/v1/api/jobs/us/search/1"))
                .withQueryParam("what", equalTo("java intern"))
                .willReturn(okJson("""
                        {"results": [
                          {"id": "1001", "title": "Java Intern", "company": {"display_name": "Acme"},
                           "location": {"display_name": "Austin, TX"}, "redirect_url": "https://adzuna.example/1001"},
                          {"id": "1002", "title": "Backend Intern", "company": {"display_name": "Globex"},
                           "location": {"display_name": "Austin, TX"}, "redirect_url": "https://adzuna.example/1002"}
                        ]}
                        """)));

        // First run: both listings are new, so exactly one email goes out
        jobAlertService.pollAllSavedSearches();

        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
        assertThat(seenListingRepository.count()).isEqualTo(2);

        // Second run: Adzuna returns the same listings, which are already seen, so no email
        jobAlertService.pollAllSavedSearches();

        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
        assertThat(seenListingRepository.count()).isEqualTo(2);
    }
}
