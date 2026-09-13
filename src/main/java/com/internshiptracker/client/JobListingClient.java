package com.internshiptracker.client;

import java.util.List;

public interface JobListingClient {

    /**
     * Searches for job listings matching the given keywords, optionally narrowed by location.
     * Returns an empty list (never null) if nothing matches or the search fails.
     */
    List<JobListing> search(String keywords, String location);
}
