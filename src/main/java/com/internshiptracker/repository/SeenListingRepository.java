package com.internshiptracker.repository;

import com.internshiptracker.entity.SeenListing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

public interface SeenListingRepository extends JpaRepository<SeenListing, UUID> {

    boolean existsBySavedSearchIdAndExternalId(UUID savedSearchId, String externalId);

    @Query("SELECT sl.externalId FROM SeenListing sl WHERE sl.savedSearch.id = :savedSearchId AND sl.externalId IN :externalIds")
    Set<String> findSeenExternalIds(UUID savedSearchId, Collection<String> externalIds);

    void deleteAllBySavedSearchId(UUID savedSearchId);
}
