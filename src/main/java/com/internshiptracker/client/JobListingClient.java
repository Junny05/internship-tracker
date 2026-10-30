package com.internshiptracker.client;

import java.util.List;

public interface JobListingClient {

    /**
     * Searches for job listings matching the given keywords, optionally narrowed by location.
     * Returns an empty list (never null) if nothing matches. Throws if the search itself
     * fails (after retrying transient failures) - callers must not treat a thrown exception
     * the same as a legitimate empty result.
     */
    List<JobListing> search(String keywords, String location);
}
