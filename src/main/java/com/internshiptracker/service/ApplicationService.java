package com.internshiptracker.service;

import com.internshiptracker.dto.ApplicationRequest;
import com.internshiptracker.dto.ApplicationResponse;
import com.internshiptracker.dto.StatusUpdateRequest;
import com.internshiptracker.entity.Application;
import com.internshiptracker.entity.ApplicationStatus;
import com.internshiptracker.entity.User;
import com.internshiptracker.exception.ResourceNotFoundException;
import com.internshiptracker.repository.ApplicationRepository;
import com.internshiptracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Page<ApplicationResponse> listForUser(UUID userId, ApplicationStatus statusFilter, Pageable pageable) {
        Page<Application> applications = statusFilter == null
                ? applicationRepository.findAllByUserId(userId, pageable)
                : applicationRepository.findAllByUserIdAndStatus(userId, statusFilter, pageable);

        return applications.map(ApplicationResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public ApplicationResponse getForUser(UUID id, UUID userId) {
        return ApplicationResponse.fromEntity(findOwnedOrThrow(id, userId));
    }

    @Transactional
    public ApplicationResponse create(ApplicationRequest request, UUID userId) {
        User user = userRepository.getReferenceById(userId);

        Application application = Application.builder()
                .company(request.getCompany())
                .role(request.getRole())
                .status(request.getStatus())
                .appliedDate(request.getAppliedDate())
                .notes(request.getNotes())
                .link(request.getLink())
                .user(user)
                .build();

        applicationRepository.save(application);
        return ApplicationResponse.fromEntity(application);
    }

    @Transactional
    public ApplicationResponse update(UUID id, UUID userId, ApplicationRequest request) {
        Application application = findOwnedOrThrow(id, userId);

        application.setCompany(request.getCompany());
        application.setRole(request.getRole());
        application.setStatus(request.getStatus());
        application.setAppliedDate(request.getAppliedDate());
        application.setNotes(request.getNotes());
        application.setLink(request.getLink());

        return ApplicationResponse.fromEntity(application);
    }

    @Transactional
    public ApplicationResponse updateStatus(UUID id, UUID userId, StatusUpdateRequest request) {
        Application application = findOwnedOrThrow(id, userId);
        application.setStatus(request.getStatus());
        return ApplicationResponse.fromEntity(application);
    }

    @Transactional
    public void delete(UUID id, UUID userId) {
        Application application = findOwnedOrThrow(id, userId);
        applicationRepository.delete(application);
    }

    private Application findOwnedOrThrow(UUID id, UUID userId) {
        return applicationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found"));
    }
}
