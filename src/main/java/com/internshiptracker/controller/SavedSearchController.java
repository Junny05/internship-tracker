package com.internshiptracker.controller;

import com.internshiptracker.dto.SavedSearchRequest;
import com.internshiptracker.dto.SavedSearchResponse;
import com.internshiptracker.security.UserPrincipal;
import com.internshiptracker.service.SavedSearchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/searches")
@RequiredArgsConstructor
public class SavedSearchController {

    private final SavedSearchService savedSearchService;

    @GetMapping
    public ResponseEntity<List<SavedSearchResponse>> list(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(savedSearchService.listForUser(principal.getId()));
    }

    @PostMapping
    public ResponseEntity<SavedSearchResponse> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody SavedSearchRequest request) {
        SavedSearchResponse response = savedSearchService.create(request, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        savedSearchService.delete(id, principal.getId());
        return ResponseEntity.noContent().build();
    }
}
