package com.internshiptracker.dto;

import com.internshiptracker.entity.ApplicationStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StatusUpdateRequest {

    @NotNull
    private ApplicationStatus status;
}
