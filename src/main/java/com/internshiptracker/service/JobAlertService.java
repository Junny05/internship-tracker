package com.internshiptracker.service;

import com.internshiptracker.client.JobListing;
import com.internshiptracker.client.JobListingClient;
import com.internshiptracker.entity.SavedSearch;
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

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class JobAlertService {

    private static final Logger log = LoggerFactory.getLogger(JobAlertService.class);

    private final SavedSearchRepository savedSearchRepository;
    private final SeenListingRepository seenListingRepository;
    private final JobListingClient jobListingClient;
    private final JavaMailSender mailSender;
    private final SeenListingRecorder seenListingRecorder;

    @Value("${app.mail.from}")
    private String fromAddress;

    // No @Transactional here: each search is checked and (on success) persisted
    // independently via SeenListingRecorder, in its own transaction, so one search's
    // failure can't roll back SeenListing rows already committed for another search
    // earlier in this same loop.
    // initialDelayString matters: fixedRateString alone fires the first run immediately
    // on startup (confirmed by running JobAlertIntegrationTest in isolation - it raced
    // with that startup firing and sent the same alert twice). Delaying the first run
    // also avoids hammering Adzuna's rate-limited free tier on every app restart/deploy.
    @Scheduled(fixedRateString = "${app.job-alerts.poll-interval-ms}",
            initialDelayString = "${app.job-alerts.poll-interval-ms}")
    public void pollAllSavedSearches() {
        List<SavedSearch> searches = savedSearchRepository.findAllWithUser();
        log.info("Polling {} saved search(es) for new listings", searches.size());

        for (SavedSearch search : searches) {
            try {
                checkSearch(search);
            } catch (Exception e) {
                log.error("Failed to poll saved search {}", search.getId(), e);
            }
        }
    }

    public void checkSearch(SavedSearch search) {
        List<JobListing> listings = jobListingClient.search(search.getKeywords(), search.getLocation());

        if (listings.isEmpty()) {
            return;
        }

        // Batched lookup instead of one exists query per listing (N+1).
        Set<String> seenExternalIds = seenListingRepository.findSeenExternalIds(
                search.getId(), listings.stream().map(JobListing::externalId).toList());

        List<JobListing> newListings = listings.stream()
                .filter(listing -> !seenExternalIds.contains(listing.externalId()))
                .toList();

        if (newListings.isEmpty()) {
            return;
        }

        // Only record listings as seen once the email actually goes out - sendNotificationEmail
        // now lets a send failure propagate (instead of swallowing it), so on failure we return
        // here without persisting anything, and the same listings are retried next poll.
        sendNotificationEmail(search, newListings);
        seenListingRecorder.markSeen(search, newListings);
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

        // Let a send failure propagate to checkSearch, which must not mark these listings
        // as seen if the email never went out.
        mailSender.send(message);
        log.info("Sent job alert email to {} with {} new listing(s)", to, newListings.size());
    }
}
