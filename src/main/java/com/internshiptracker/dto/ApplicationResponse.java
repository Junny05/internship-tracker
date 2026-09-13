package com.internshiptracker.dto;

import com.internshiptracker.entity.Application;
import com.internshiptracker.entity.ApplicationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApplicationResponse {

    private UUID id;
    private String company;
    private String role;
    private ApplicationStatus status;
    private LocalDate appliedDate;
    private String notes;
    private String link;

    public static ApplicationResponse fromEntity(Application application) {
        return ApplicationResponse.builder()
                .id(application.getId())
                .company(application.getCompany())
                .role(application.getRole())
                .status(application.getStatus())
                .appliedDate(application.getAppliedDate())
                .notes(application.getNotes())
                .link(application.getLink())
                .build();
    }
}
