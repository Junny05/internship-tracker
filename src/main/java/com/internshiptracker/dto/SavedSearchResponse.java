package com.internshiptracker.dto;

import com.internshiptracker.entity.SavedSearch;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SavedSearchResponse {

    private UUID id;
    private String keywords;
    private String location;
    private Instant createdAt;

    public static SavedSearchResponse fromEntity(SavedSearch savedSearch) {
        return SavedSearchResponse.builder()
                .id(savedSearch.getId())
                .keywords(savedSearch.getKeywords())
                .location(savedSearch.getLocation())
                .createdAt(savedSearch.getCreatedAt())
                .build();
    }
}
