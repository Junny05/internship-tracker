package com.internshiptracker.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "seen_listings",
        uniqueConstraints = @UniqueConstraint(columnNames = {"saved_search_id", "external_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeenListing {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "saved_search_id", nullable = false)
    private SavedSearch savedSearch;

    @Column(name = "external_id", nullable = false)
    private String externalId;

    @Column(nullable = false)
    private Instant seenAt;

    @PrePersist
    protected void onCreate() {
        if (seenAt == null) {
            seenAt = Instant.now();
        }
    }
}
