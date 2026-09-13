package com.internshiptracker.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SavedSearchRequest {

    @NotBlank
    private String keywords;

    private String location;
}
