package com.internshiptracker.repository;

import com.internshiptracker.entity.Application;
import com.internshiptracker.entity.ApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApplicationRepository extends JpaRepository<Application, UUID> {

    List<Application> findAllByUserId(UUID userId);

    List<Application> findAllByUserIdAndStatus(UUID userId, ApplicationStatus status);

    Optional<Application> findByIdAndUserId(UUID id, UUID userId);
}
