package com.internshiptracker.service;

import com.internshiptracker.dto.SavedSearchRequest;
import com.internshiptracker.dto.SavedSearchResponse;
import com.internshiptracker.entity.SavedSearch;
import com.internshiptracker.entity.User;
import com.internshiptracker.exception.ResourceNotFoundException;
import com.internshiptracker.repository.SavedSearchRepository;
import com.internshiptracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SavedSearchService {

    private final SavedSearchRepository savedSearchRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<SavedSearchResponse> listForUser(UUID userId) {
        return savedSearchRepository.findAllByUserId(userId).stream()
                .map(SavedSearchResponse::fromEntity)
                .toList();
    }

    @Transactional
    public SavedSearchResponse create(SavedSearchRequest request, UUID userId) {
        User user = userRepository.getReferenceById(userId);

        SavedSearch savedSearch = SavedSearch.builder()
                .keywords(request.getKeywords())
                .location(request.getLocation())
                .user(user)
                .build();

        savedSearchRepository.save(savedSearch);
        return SavedSearchResponse.fromEntity(savedSearch);
    }

    @Transactional
    public void delete(UUID id, UUID userId) {
        SavedSearch savedSearch = savedSearchRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Saved search not found"));
        savedSearchRepository.delete(savedSearch);
    }
}
