package com.internshiptracker.service;

import com.internshiptracker.client.JobListing;
import com.internshiptracker.client.JobListingClient;
import com.internshiptracker.entity.SavedSearch;
import com.internshiptracker.entity.SeenListing;
import com.internshiptracker.repository.SavedSearchRepository;
import com.internshiptracker.repository.SeenListingRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class JobAlertService {

    private static final Logger log = LoggerFactory.getLogger(JobAlertService.class);

    private final SavedSearchRepository savedSearchRepository;
    private final SeenListingRepository seenListingRepository;
    private final JobListingClient jobListingClient;
    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String fromAddress;

    @Scheduled(fixedRateString = "${app.job-alerts.poll-interval-ms}")
    public void pollAllSavedSearches() {
        List<SavedSearch> searches = savedSearchRepository.findAll();
        log.info("Polling {} saved search(es) for new listings", searches.size());

        for (SavedSearch search : searches) {
            try {
                checkSearch(search);
            } catch (Exception e) {
                log.error("Failed to poll saved search {}", search.getId(), e);
            }
        }
    }

    @Transactional
    public void checkSearch(SavedSearch search) {
        List<JobListing> listings = jobListingClient.search(search.getKeywords(), search.getLocation());

        List<JobListing> newListings = listings.stream()
                .filter(listing -> !seenListingRepository.existsBySavedSearchIdAndExternalId(
                        search.getId(), listing.externalId()))
                .toList();

        if (newListings.isEmpty()) {
            return;
        }

        for (JobListing listing : newListings) {
            seenListingRepository.save(SeenListing.builder()
                    .savedSearch(search)
                    .externalId(listing.externalId())
                    .seenAt(Instant.now())
                    .build());
        }

        sendNotificationEmail(search, newListings);
    }

    private void sendNotificationEmail(SavedSearch search, List<JobListing> newListings) {
        String to = search.getUser().getEmail();
        String subject = newListings.size() == 1
                ? "New internship match: " + newListings.get(0).title()
                : newListings.size() + " new internship matches for \"" + search.getKeywords() + "\"";

        StringBuilder body = new StringBuilder();
        body.append("New postings matching \"").append(search.getKeywords()).append("\"");
        if (search.getLocation() != null && !search.getLocation().isBlank()) {
            body.append(" in ").append(search.getLocation());
        }
        body.append(":\n\n");

        for (JobListing listing : newListings) {
            body.append("- ").append(listing.title()).append(" at ").append(listing.company());
            if (listing.location() != null) {
                body.append(" (").append(listing.location()).append(")");
            }
            body.append("\n  ").append(listing.url()).append("\n\n");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body.toString());

        try {
            mailSender.send(message);
            log.info("Sent job alert email to {} with {} new listing(s)", to, newListings.size());
        } catch (Exception e) {
            log.error("Failed to send job alert email to {}", to, e);
        }
    }
}
