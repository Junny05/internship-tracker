package com.internshiptracker.repository;

import com.internshiptracker.entity.SavedSearch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SavedSearchRepository extends JpaRepository<SavedSearch, UUID> {

    // Not paginated like ApplicationRepository.findAllByUserId - saved searches
    // per user stay small (a handful at most), unlike applications which can
    // accumulate over years of job hunting.
    List<SavedSearch> findAllByUserId(UUID userId);

    Optional<SavedSearch> findByIdAndUserId(UUID id, UUID userId);

    // Used by the job alert poller, which reads search.getUser() outside of any open
    // transaction (each search is processed and persisted independently - see
    // JobAlertService/SeenListingRecorder) - without the join fetch that access would
    // throw LazyInitializationException.
    @Query("SELECT s FROM SavedSearch s JOIN FETCH s.user")
    List<SavedSearch> findAllWithUser();
}
