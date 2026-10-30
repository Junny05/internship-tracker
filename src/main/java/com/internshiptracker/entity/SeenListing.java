package com.internshiptracker.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "seen_listings",
        uniqueConstraints = @UniqueConstraint(columnNames = {"saved_search_id", "external_id"}))
@Getter
@Setter
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SeenListing other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "SeenListing{id=" + id + ", externalId=" + externalId + ", seenAt=" + seenAt + "}";
    }
}
