package com.internshiptracker.controller;

import com.internshiptracker.dto.ApplicationRequest;
import com.internshiptracker.dto.ApplicationResponse;
import com.internshiptracker.dto.StatusUpdateRequest;
import com.internshiptracker.entity.ApplicationStatus;
import com.internshiptracker.security.UserPrincipal;
import com.internshiptracker.service.ApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/applications")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService applicationService;

    @GetMapping
    public ResponseEntity<Page<ApplicationResponse>> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) ApplicationStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(applicationService.listForUser(principal.getId(), status, pageable));
    }

    @PostMapping
    public ResponseEntity<ApplicationResponse> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ApplicationRequest request) {
        ApplicationResponse response = applicationService.create(request, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApplicationResponse> get(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        return ResponseEntity.ok(applicationService.getForUser(id, principal.getId()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApplicationResponse> update(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody ApplicationRequest request) {
        return ResponseEntity.ok(applicationService.update(id, principal.getId(), request));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApplicationResponse> updateStatus(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody StatusUpdateRequest request) {
        return ResponseEntity.ok(applicationService.updateStatus(id, principal.getId(), request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        applicationService.delete(id, principal.getId());
        return ResponseEntity.noContent().build();
    }
}
