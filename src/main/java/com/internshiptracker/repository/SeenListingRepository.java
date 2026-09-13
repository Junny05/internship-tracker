package com.internshiptracker.repository;

import com.internshiptracker.entity.SeenListing;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SeenListingRepository extends JpaRepository<SeenListing, UUID> {

    boolean existsBySavedSearchIdAndExternalId(UUID savedSearchId, String externalId);
}
