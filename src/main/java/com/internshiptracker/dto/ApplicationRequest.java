package com.internshiptracker.dto;

import com.internshiptracker.entity.ApplicationStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class ApplicationRequest {

    @NotBlank
    private String company;

    @NotBlank
    private String role;

    @NotNull
    private ApplicationStatus status;

    private LocalDate appliedDate;

    private String notes;

    private String link;
}
