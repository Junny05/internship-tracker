package com.internshiptracker.repository;

import com.internshiptracker.entity.Application;
import com.internshiptracker.entity.ApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ApplicationRepository extends JpaRepository<Application, UUID> {

    Page<Application> findAllByUserId(UUID userId, Pageable pageable);

    Page<Application> findAllByUserIdAndStatus(UUID userId, ApplicationStatus status, Pageable pageable);

    Optional<Application> findByIdAndUserId(UUID id, UUID userId);
}
