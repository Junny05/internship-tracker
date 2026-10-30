package com.internshiptracker.service;

import com.internshiptracker.client.JobListing;
import com.internshiptracker.entity.SavedSearch;
import com.internshiptracker.entity.SeenListing;
import com.internshiptracker.repository.SeenListingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * A separate bean (not a method on JobAlertService) so this persistence step gets its
 * own transaction per saved search, through Spring's proxy, without relying on
 * self-invocation of a @Transactional method on the same instance (which Spring's
 * proxy-based AOP does not intercept).
 */
@Service
@RequiredArgsConstructor
public class SeenListingRecorder {

    private final SeenListingRepository seenListingRepository;

    @Transactional
    public void markSeen(SavedSearch search, List<JobListing> listings) {
        List<SeenListing> rows = listings.stream()
                .map(listing -> SeenListing.builder()
                        .savedSearch(search)
                        .externalId(listing.externalId())
                        .seenAt(Instant.now())
                        .build())
                .toList();

        seenListingRepository.saveAll(rows);
    }
}
